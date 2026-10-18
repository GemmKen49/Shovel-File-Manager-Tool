package com.shovel.filemanager.data

import android.content.Context
import com.shovel.filemanager.archive.ArchiveEngine
import com.shovel.filemanager.archive.ArchiveFormat
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * What is inside a folder or archive, counting only visible items.
 *
 * [itemCount] counts the direct children (a sub-folder counts as one). [totalBytes] adds up every visible file
 * in the whole tree below. The "truncated" flags mean a safety limit stopped the scan, so numbers are lower bounds.
 */
data class FolderStats(
    val itemCount: Int,
    val totalBytes: Long,
    val itemsTruncated: Boolean,
    val sizeTruncated: Boolean,
)

/** "12 items", "1 item" or "50,000+ items". */
fun FolderStats.itemsText(): String = when {
    itemsTruncated -> String.format("%,d+ items", itemCount)
    itemCount == 1 -> "1 item"
    else -> String.format("%,d items", itemCount)
}

/** "3.4 MB", or "3.4 MB+" when the scan stopped early. */
fun FolderStats.sizeText(): String = FileSystemRules.humanBytes(totalBytes) + if (sizeTruncated) "+" else ""

/** Counts and sizes folder and archive contents in the background, with a cache so scrolling stays cheap. */
class FolderStatsRepository(context: Context, private val archives: ArchiveEngine) {
    private val preferences = context.getSharedPreferences("folder_details", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    /** Whether the file list shows the details under folders and archives. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    private class Cached(val stats: FolderStats?, val atMillis: Long)

    private val cache = object : LinkedHashMap<String, Cached>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > MAX_CACHED
    }

    // At most a couple of scans at once so a screen full of folders cannot starve everything else.
    private val gate = Semaphore(MAX_PARALLEL_SCANS)

    suspend fun statsFor(entry: FileEntry, includeHidden: Boolean): FolderStats? {
        if (!entry.isDirectory && entry.kind != EntryKind.ARCHIVE) return null
        val key = "${entry.absolutePath}|${entry.modifiedAtMillis}|${entry.sizeBytes}|$includeHidden"
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val cached = cache[key]
            if (cached != null && now - cached.atMillis < CACHE_LIFETIME_MILLIS) return cached.stats
        }
        val stats = gate.withPermit {
            withContext(Dispatchers.IO) {
                if (entry.isDirectory) scanDirectory(entry.file, includeHidden) else scanArchive(entry.file, includeHidden)
            }
        }
        synchronized(cache) { cache[key] = Cached(stats, now) }
        return stats
    }

    private suspend fun scanDirectory(root: File, includeHidden: Boolean): FolderStats? {
        val top = root.listFiles() ?: return null
        var visited = 0
        var directItems = 0
        var totalBytes = 0L
        var sizeTruncated = false
        val pending = ArrayDeque<File>()
        for (child in top) {
            if (!includeHidden && child.isHidden) continue
            directItems += 1
            visited += 1
            if (isRealDirectory(child)) pending.addLast(child) else totalBytes += fileSize(child)
            if (visited >= SCAN_LIMIT) {
                sizeTruncated = true
                break
            }
        }
        val itemsTruncated = directItems >= SCAN_LIMIT
        while (pending.isNotEmpty() && !sizeTruncated) {
            currentCoroutineContext().ensureActive()
            val directory = pending.removeLast()
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (!includeHidden && child.isHidden) continue
                visited += 1
                if (isRealDirectory(child)) pending.addLast(child) else totalBytes += fileSize(child)
                if (visited >= SCAN_LIMIT) {
                    sizeTruncated = true
                    break
                }
            }
        }
        return FolderStats(directItems, totalBytes, itemsTruncated, sizeTruncated)
    }

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

    private fun isRealDirectory(file: File): Boolean =
        file.isDirectory && !runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun fileSize(file: File): Long =
        if (runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)) 0L else file.length()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val SCAN_LIMIT = 50_000
        const val MAX_PARALLEL_SCANS = 2
        const val MAX_CACHED = 2_000
        const val CACHE_LIFETIME_MILLIS = 30_000L
    }
}
