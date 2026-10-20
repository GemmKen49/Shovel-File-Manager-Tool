package com.shovel.filemanager.data

import android.content.Context
import android.os.Process
import com.shovel.filemanager.archive.ArchiveEngine
import com.shovel.filemanager.archive.ArchiveFormat
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors

/**
 * What is inside a folder or archive, counting only visible items.
 *
 * [itemCount] counts the direct children (a sub-folder counts as one). [totalBytes] adds up every visible file
 * in the whole tree below. The "truncated" flags mean a safety limit stopped the scan, so numbers are lower bounds.
 * [sizeReady] is false while only the quick item count is known and the size is still being added up.
 */
data class FolderStats(
    val itemCount: Int,
    val totalBytes: Long,
    val itemsTruncated: Boolean,
    val sizeTruncated: Boolean,
    val sizeReady: Boolean = true,
    /** False for system folders such as /proc and /sys, where adding up sizes would be slow and meaningless. */
    val sizeAvailable: Boolean = true,
)

/** "12 items", "1 item" or "50,000+ items". */
fun FolderStats.itemsText(): String = when {
    itemsTruncated -> String.format("%,d+ items", itemCount)
    itemCount == 1 -> "1 item"
    else -> String.format("%,d items", itemCount)
}

/** "3.4 MB", or "3.4 MB+" when the scan stopped early. */
fun FolderStats.sizeText(): String = FileSystemRules.humanBytes(totalBytes) + if (sizeTruncated) "+" else ""

/**
 * Counts and sizes folder and archive contents in the background.
 *
 * Kept cheap on purpose: the item count appears after one directory read, the size follows from a single
 * file-attribute read per entry, everything runs on two low-priority threads, and results are cached and
 * shown immediately (then quietly refreshed) the next time the same folder scrolls into view.
 */
class FolderStatsRepository(context: Context, private val archives: ArchiveEngine) {
    private val preferences = context.getSharedPreferences("folder_details", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    /** Whether the file list shows the details under folders and archives. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    private class Cached(val stats: FolderStats?, val modifiedAtMillis: Long, val sizeBytes: Long, val atMillis: Long)

    private val cache = object : LinkedHashMap<String, Cached>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > MAX_CACHED
    }

    private fun keyOf(entry: FileEntry, includeHidden: Boolean) = "${entry.absolutePath}|$includeHidden"

    /** The last known numbers for this entry, instantly and without any file access. */
    fun cached(entry: FileEntry, includeHidden: Boolean): FolderStats? =
        synchronized(cache) { cache[keyOf(entry, includeHidden)]?.stats }

    /** Final numbers only. Used where nothing needs to appear before the scan is done. */
    suspend fun statsFor(entry: FileEntry, includeHidden: Boolean): FolderStats? = load(entry, includeHidden) { }

    /**
     * Delivers progressively through [onUpdate]: the cached numbers first (if any), then the quick item count,
     * then the final numbers. Returns the final numbers, or null when this entry has nothing to show.
     */
    suspend fun load(entry: FileEntry, includeHidden: Boolean, onUpdate: (FolderStats) -> Unit): FolderStats? {
        if (!entry.isDirectory && entry.kind != EntryKind.ARCHIVE) return null
        val key = keyOf(entry, includeHidden)
        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[key] }
        if (cached != null) {
            cached.stats?.let(onUpdate)
            // A folder's own size field is only filled in while sorting by size, so it says nothing about changes.
            val unchanged = cached.modifiedAtMillis == entry.modifiedAtMillis &&
                (entry.isDirectory || cached.sizeBytes == entry.sizeBytes)
            if (unchanged && now - cached.atMillis < FRESH_MILLIS) return cached.stats
        }
        val result: FolderStats? = if (entry.isDirectory) {
            // Resolve links first: "/sdcard" or "/etc" are links to folders, and a link is not walked as a folder.
            val real = runCatching { entry.file.canonicalFile }.getOrDefault(entry.file)
            val quick = withContext(scanDispatcher) { quickCount(real, includeHidden) } ?: return null
            if (isVirtualFileSystem(real.path)) {
                FolderStats(quick.first, 0L, quick.second, sizeTruncated = false, sizeReady = true, sizeAvailable = false)
            } else {
                if (cached?.stats == null) {
                    onUpdate(FolderStats(quick.first, 0L, quick.second, sizeTruncated = false, sizeReady = false))
                }
                withContext(scanDispatcher) {
                    scanTree(real, includeHidden, quick.first, quick.second) { bytesSoFar ->
                        onUpdate(FolderStats(quick.first, bytesSoFar, quick.second, sizeTruncated = false, sizeReady = false))
                    }
                }
            }
        } else {
            withContext(scanDispatcher) { scanArchive(entry.file, includeHidden) }
        }
        synchronized(cache) { cache[key] = Cached(result, entry.modifiedAtMillis, entry.sizeBytes, System.currentTimeMillis()) }
        result?.let(onUpdate)
        return result
    }

    /** One directory read, no per-file calls. Returns the visible direct-child count and whether it hit the limit. */
    private fun quickCount(root: File, includeHidden: Boolean): Pair<Int, Boolean>? {
        val names = root.list() ?: return null
        var count = 0
        for (name in names) if (includeHidden || !name.startsWith(".")) count += 1
        return minOf(count, SCAN_LIMIT) to (count >= SCAN_LIMIT)
    }

    /** Adds up every visible file below [root] with one attribute read per entry. Symlinks are not followed. */
    private suspend fun scanTree(
        root: File,
        includeHidden: Boolean,
        directItems: Int,
        itemsTruncated: Boolean,
        onProgress: (Long) -> Unit,
    ): FolderStats {
        val job = currentCoroutineContext()[Job]
        val rootPath = root.toPath()
        val startNanos = System.nanoTime()
        var lastPublishNanos = startNanos
        var visited = 0
        var totalBytes = 0L
        var sizeTruncated = false

        // Every few hundred entries: stop if cancelled or out of time, and share the running total.
        fun shouldStop(): Boolean {
            if (job?.isActive == false) return true
            val now = System.nanoTime()
            if (now - startNanos > TIME_BUDGET_NANOS) {
                sizeTruncated = true
                return true
            }
            if (now - lastPublishNanos > PUBLISH_INTERVAL_NANOS) {
                lastPublishNanos = now
                onProgress(totalBytes)
            }
            return false
        }

        val visitor = object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir == rootPath) return FileVisitResult.CONTINUE
                if (!includeHidden && isHiddenName(dir)) return FileVisitResult.SKIP_SUBTREE
                visited += 1
                if (visited >= SCAN_LIMIT) {
                    sizeTruncated = true
                    return FileVisitResult.TERMINATE
                }
                if ((visited and CHECK_MASK) == 0 && shouldStop()) return FileVisitResult.TERMINATE
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!includeHidden && isHiddenName(file)) return FileVisitResult.CONTINUE
                visited += 1
                if (attrs.isRegularFile) totalBytes += attrs.size()
                if (visited >= SCAN_LIMIT) {
                    sizeTruncated = true
                    return FileVisitResult.TERMINATE
                }
                if ((visited and CHECK_MASK) == 0 && shouldStop()) return FileVisitResult.TERMINATE
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        }
        Files.walkFileTree(rootPath, emptySet(), Int.MAX_VALUE, visitor)
        currentCoroutineContext().ensureActive()
        return FolderStats(directItems, totalBytes, itemsTruncated, sizeTruncated, sizeReady = true)
    }

    /** System folders whose "files" are generated on the fly: reading them is slow and their sizes mean nothing. */
    private fun isVirtualFileSystem(path: String): Boolean =
        VIRTUAL_ROOTS.any { root -> path == root || path.startsWith("$root/") }

    private fun isHiddenName(path: Path): Boolean = path.fileName?.toString()?.startsWith(".") == true

    /** Only the cheap, header-based formats are scanned; encrypted or streamed archives are skipped. */
    private suspend fun scanArchive(file: File, includeHidden: Boolean): FolderStats? {
        val format = runCatching { archives.detectFormat(file) }.getOrNull() ?: return null
        if (format != ArchiveFormat.ZIP && format != ArchiveFormat.SEVEN_Z) return null
        val entries = runCatching { archives.list(file) }.getOrNull() ?: return null
        val topLevel = HashSet<String>()
        var totalBytes = 0L
        for (entry in entries) {
            val segments = entry.name.trim('/').split('/').filter(String::isNotEmpty)
            if (segments.isEmpty()) continue
            if (!includeHidden && segments.any { it.startsWith(".") }) continue
            topLevel += segments.first()
            if (!entry.directory && entry.sizeBytes > 0L) totalBytes += entry.sizeBytes
        }
        return FolderStats(topLevel.size, totalBytes, itemsTruncated = false, sizeTruncated = false)
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val SCAN_LIMIT = 50_000
        const val SCAN_THREADS = 2
        const val MAX_CACHED = 2_000
        const val FRESH_MILLIS = 5 * 60_000L
        const val CHECK_MASK = 0xFF
        const val TIME_BUDGET_NANOS = 2_000_000_000L
        const val PUBLISH_INTERVAL_NANOS = 250_000_000L
        val VIRTUAL_ROOTS = listOf("/proc", "/sys", "/dev", "/acct", "/config", "/debug_ramdisk", "/d")

        // Two background-priority threads: scanning must never compete with scrolling or thumbnail loading.
        val scanDispatcher = Executors.newFixedThreadPool(SCAN_THREADS) { runnable ->
            Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    runnable.run()
                },
                "folder-stats",
            ).apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }
}
