package com.shovel.filemanager.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.operations.OperationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SafLocation(
    val uri: String,
    val title: String,
    val providerPackageName: String? = null,
    val providerLabel: String? = null,
    val folderName: String? = null,
    val canWrite: Boolean = false,
    val customTitle: String? = null,
)

internal object SafLocationRules {
    private const val MAX_TITLE_LENGTH = 120

    fun normalizeTitle(value: String): String {
        val title = value.trim()
        require(title.isNotEmpty()) { "The name cannot be empty" }
        require(title.length <= MAX_TITLE_LENGTH) { "The connection name is too long" }
        require(title.none(Char::isISOControl)) { "The name contains invalid characters" }
        return title
    }
}

data class SafEntry(
    val uri: String,
    val name: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val mimeType: String?,
    val kind: EntryKind,
    val canWrite: Boolean,
)

class SafFileRepository(private val context: Context, private val onMutation: suspend (List<File>) -> Unit = {}) {
    companion object {
        private const val PREFS = "saf_locations_v1"
        private const val KEY_LOCATIONS = "locations"
        private const val MAX_LOCATIONS = 100
        private const val MAX_DIRECTORY_ENTRIES = 50_000
        private const val MAX_COPY_ENTRIES = 100_000
        private const val MAX_DEPTH = 64
        private const val BUFFER_SIZE = 256 * 1_024
    }

    private val resolver: ContentResolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun locations(): List<SafLocation> = withContext(Dispatchers.IO) {
        val array = JSONArray(preferences.getString(KEY_LOCATIONS, "[]") ?: "[]")
        require(array.length() <= MAX_LOCATIONS) { "The SAF location limit was exceeded" }
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            describeLocation(
                uri = Uri.parse(item.getString("uri")),
                storedTitle = item.optionalString("title"),
                storedCustomTitle = item.optionalString("customTitle"),
                storedProviderPackage = item.optionalString("providerPackage"),
                storedProviderLabel = item.optionalString("providerLabel"),
                storedFolderName = item.optionalString("folderName"),
            )
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    suspend fun addLocation(uri: Uri, title: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val permissionBefore = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            try {
                persistBestAvailablePermission(uri)
                val root = DocumentFile.fromTreeUri(context, uri)
                    ?: throw IllegalArgumentException("The selected document location is unavailable")
                require(root.isDirectory) { "The selected location is not a folder" }
                val current = locations().toMutableList().apply { removeAll { it.uri == uri.toString() } }
                current += describeLocation(uri, storedTitle = title.takeIf(String::isNotBlank))
                require(current.size <= MAX_LOCATIONS) { "Too many locations selected" }
                writeLocations(current)
            } catch (error: Throwable) {
                releasePermissionAddedAfter(uri, permissionBefore?.isReadPermission == true, permissionBefore?.isWritePermission == true)
                throw error
            }
        }
    }

    suspend fun removeLocation(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updated = locations().filterNot { it.uri == uri }
            writeLocations(updated)
            releasePersistedPermission(Uri.parse(uri))
            Unit
        }
    }

    suspend fun renameLocation(uri: String, requestedTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val title = SafLocationRules.normalizeTitle(requestedTitle)
            var found = false
            val updated = locations().map { location ->
                if (location.uri == uri) {
                    found = true
                    location.copy(title = title, customTitle = title)
                } else {
                    location
                }
            }
            require(found) { "The location no longer exists" }
            writeLocations(updated)
        }
    }

    suspend fun list(treeOrDirectoryUri: String): Result<List<SafEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(treeOrDirectoryUri)
            val directory = document(uri)
            require(directory.isDirectory) { "This is not a folder" }
            val files = directory.listFiles()
            require(files.size <= MAX_DIRECTORY_ENTRIES) { "The folder contains too many items" }
            files.map(::toEntry).sortedWith(compareByDescending<SafEntry> { it.directory }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun createDirectory(parentUri: String, name: String): Result<SafEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = document(parentUri)
            val created = parent.createDirectory(name) ?: throw IllegalStateException("Could not create the folder")
            toEntry(created)
        }
    }

    suspend fun createFile(parentUri: String, name: String, mimeType: String = "application/octet-stream"): Result<SafEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = document(parentUri)
            val created = parent.createFile(mimeType, name) ?: throw IllegalStateException("Could not create the file")
            toEntry(created)
        }
    }

    suspend fun rename(uri: String, name: String): Result<SafEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val file = document(uri)
            check(file.renameTo(name)) { "Could not rename" }
            toEntry(file)
        }
    }

    suspend fun delete(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { check(document(uri).delete()) { "Could not delete" } }
    }

    suspend fun selectionInfo(uris: Collection<String>): Result<FileSelectionSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val roots = uris.map(String::trim).filter(String::isNotEmpty).distinct()
            require(roots.isNotEmpty() && roots.size <= 10_000) { "Invalid number of selected items" }
            data class Pending(val file: DocumentFile, val depth: Int, val selectedRoot: Boolean)
            val pending = ArrayDeque<Pending>()
            roots.asReversed().forEach { pending.add(Pending(document(it), 0, true)) }
            val seen = hashSetOf<String>()
            var files = 0
            var folders = 0
            var bytes = 0L
            var scanned = 0
            var complete = true
            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                if (scanned >= MAX_COPY_ENTRIES) { complete = false; break }
                val current = pending.removeLast()
                if (!seen.add(current.file.uri.toString())) { complete = false; continue }
                scanned++
                when {
                    current.file.isDirectory -> {
                        if (!current.selectedRoot) folders++
                        if (current.depth >= MAX_DEPTH) { complete = false; continue }
                        val children = runCatching { current.file.listFiles() }.getOrElse { complete = false; emptyArray() }
                        if (children.size > MAX_DIRECTORY_ENTRIES) complete = false
                        children.take(MAX_DIRECTORY_ENTRIES).asReversed().forEach {
                            pending.add(Pending(it, current.depth + 1, false))
                        }
                    }
                    current.file.isFile -> {
                        files++
                        val value = current.file.length().coerceAtLeast(0L)
                        bytes = if (value > Long.MAX_VALUE - bytes) Long.MAX_VALUE else bytes + value
                    }
                    else -> complete = false
                }
            }
            FileSelectionSummary(roots.size, files, folders, bytes, scanned, complete)
        }
    }

    suspend fun copyFromLocal(source: File, parentUri: String, operation: OperationContext? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(source.exists()) { "The local source no longer exists" }
                val counter = CopyCounter()
                copyLocalNode(source, document(parentUri), operation, counter, 0)
            }
        }

    suspend fun copyToLocal(sourceUri: String, destinationDirectory: File, operation: OperationContext? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(destinationDirectory.isDirectory) { "The local destination folder is unavailable" }
                val counter = CopyCounter()
                val target = copySafNode(document(sourceUri), destinationDirectory, operation, counter, 0)
                onMutation(listOf(target))
            }
        }

    private fun document(uri: String): DocumentFile = document(Uri.parse(uri))

    private fun document(uri: Uri): DocumentFile = if (usesPersistedTreeAccess(uri)) {
        DocumentFile.fromTreeUri(context, uri)
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }
        ?: throw IllegalArgumentException("The document is unavailable")

    /**
     * Both the selected root and every child returned from it retain the original `tree` segment.
     * DocumentFile 1.1 resolves the active document segment while preserving that tree grant, so
     * child folders must also remain tree-backed or listing/creation becomes unsupported.
     */
    internal fun usesPersistedTreeAccess(uri: Uri): Boolean = DocumentsContract.isTreeUri(uri)

    /** Distinguishes the originally selected root for labels and navigation boundaries. */
    internal fun isTreeRootUri(uri: Uri): Boolean {
        if (!DocumentsContract.isTreeUri(uri)) return false
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        return documentId == null || documentId == treeId
    }

    private fun persistBestAvailablePermission(uri: Uri) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val combined = runCatching { resolver.takePersistableUriPermission(uri, read or write) }
        if (combined.isSuccess) return
        runCatching { resolver.takePersistableUriPermission(uri, read) }
            .getOrElse { throw combined.exceptionOrNull() ?: it }
    }

    private fun releasePersistedPermission(uri: Uri) {
        val permission = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (flags != 0) runCatching { resolver.releasePersistableUriPermission(uri, flags) }
    }

    private fun releasePermissionAddedAfter(uri: Uri, hadRead: Boolean, hadWrite: Boolean) {
        val permission = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val addedFlags = (if (permission.isReadPermission && !hadRead) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission && !hadWrite) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (addedFlags != 0) runCatching { resolver.releasePersistableUriPermission(uri, addedFlags) }
    }

    @Suppress("DEPRECATION")
    private fun describeLocation(
        uri: Uri,
        storedTitle: String? = null,
        storedCustomTitle: String? = null,
        storedProviderPackage: String? = null,
        storedProviderLabel: String? = null,
        storedFolderName: String? = null,
    ): SafLocation {
        val provider = uri.authority?.let { authority -> context.packageManager.resolveContentProvider(authority, 0) }
        val packageName = provider?.packageName ?: storedProviderPackage
        val providerLabel = provider?.loadLabel(context.packageManager)?.toString()?.takeIf(String::isNotBlank)
            ?: storedProviderLabel
        val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        val folderName = root?.name?.takeIf(String::isNotBlank)
            ?: storedFolderName
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':') }
                .getOrNull()?.takeIf { it.isNotBlank() && it.length <= 120 }
        val title = storedCustomTitle ?: providerLabel ?: storedTitle ?: folderName ?: "Android documents"
        return SafLocation(
            uri = uri.toString(),
            title = title,
            customTitle = storedCustomTitle,
            providerPackageName = packageName,
            providerLabel = providerLabel,
            folderName = folderName,
            canWrite = root?.canWrite() == true,
        )
    }

    private fun toEntry(file: DocumentFile) = SafEntry(
        uri = file.uri.toString(),
        name = file.name ?: "Untitled",
        directory = file.isDirectory,
        sizeBytes = file.length().coerceAtLeast(0),
        modifiedAtMillis = file.lastModified().coerceAtLeast(0),
        mimeType = file.type,
        kind = if (file.isDirectory) EntryKind.DIRECTORY else kindFromMime(file.type, file.name),
        canWrite = file.canWrite(),
    )

    private fun kindFromMime(mime: String?, name: String?): EntryKind = when {
        mime?.startsWith("image/") == true -> EntryKind.IMAGE
        mime?.startsWith("video/") == true -> EntryKind.VIDEO
        mime?.startsWith("audio/") == true -> EntryKind.AUDIO
        name?.endsWith(".apk", ignoreCase = true) == true -> EntryKind.APK
        name?.substringAfterLast('.', "")?.lowercase() in setOf("zip", "7z", "rar", "tar", "gz") -> EntryKind.ARCHIVE
        mime?.startsWith("text/") == true || mime == "application/pdf" -> EntryKind.DOCUMENT
        else -> EntryKind.OTHER
    }

    private suspend fun copyLocalNode(
        source: File,
        destinationParent: DocumentFile,
        operation: OperationContext?,
        counter: CopyCounter,
        depth: Int,
    ) {
        countCopyNode(counter, depth)
        val safeName = FileSystemRules.validateFileName(source.name).getOrThrow()
        val targetName = freeDocumentName(destinationParent, safeName)
        if (source.isDirectory) {
            val partialName = freeDocumentName(destinationParent, ".$targetName.shovel-partial")
            val directory = destinationParent.createDirectory(partialName)
                ?: throw IllegalStateException("Could not create the SAF folder")
            try {
                val children = source.listFiles() ?: throw SecurityException("The local folder is not readable")
                require(children.size <= MAX_DIRECTORY_ENTRIES) { "The local folder contains too many items" }
                children.forEach { child -> copyLocalNode(child, directory, operation, counter, depth + 1) }
                check(directory.renameTo(targetName)) { "Could not finish the SAF folder copy" }
                operation?.progress(itemDelta = 1, currentName = source.name)
            } catch (error: Throwable) {
                directory.delete()
                throw error
            }
            return
        }
        require(source.isFile) { "Unsupported local item type" }
        val partialName = freeDocumentName(destinationParent, ".$targetName.shovel-partial")
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(source.extension.lowercase()) ?: "application/octet-stream"
        val partial = destinationParent.createFile(mime, partialName)
            ?: throw IllegalStateException("Could not create the SAF file")
        try {
            var written = 0L
            source.inputStream().buffered().use { input ->
                val output = resolver.openOutputStream(partial.uri, "w")?.buffered()
                    ?: throw IllegalStateException("The SAF file is not writable")
                output.use { sink ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        operation?.checkpoint()
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written = Math.addExact(written, read.toLong())
                        operation?.progress(byteDelta = read.toLong(), currentName = source.name)
                    }
                }
            }
            require(written == source.length()) { "The SAF copy size does not match" }
            val providerLength = partial.length()
            if (providerLength > 0) require(providerLength == source.length()) { "The SAF provider returned a different file size" }
            check(partial.renameTo(targetName)) { "Could not finish the SAF copy" }
            operation?.progress(itemDelta = 1, currentName = source.name)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private suspend fun copySafNode(
        source: DocumentFile,
        destinationParent: File,
        operation: OperationContext?,
        counter: CopyCounter,
        depth: Int,
    ): File {
        countCopyNode(counter, depth)
        val safeName = FileSystemRules.validateFileName(source.name ?: "Untitled").getOrThrow()
        val requested = File(destinationParent, safeName)
        val target = if (requested.exists()) FileSystemRules.keepBothTarget(requested) else requested
        require(target.canonicalFile.toPath().startsWith(destinationParent.canonicalFile.toPath())) { "The SAF name escapes the destination" }
        if (source.isDirectory) {
            val partialDirectory = File(destinationParent, ".${target.name}.shovel-partial-${System.nanoTime()}")
            require(partialDirectory.mkdir()) { "Could not create the temporary local folder" }
            try {
                val children = source.listFiles()
                require(children.size <= MAX_DIRECTORY_ENTRIES) { "The SAF folder contains too many items" }
                children.forEach { child -> copySafNode(child, partialDirectory, operation, counter, depth + 1) }
                require(partialDirectory.renameTo(target)) { "Could not finish the local folder copy" }
                operation?.progress(itemDelta = 1, currentName = safeName)
            } finally {
                if (partialDirectory.exists()) partialDirectory.deleteRecursively()
            }
            return target
        }
        require(source.isFile) { "Unsupported SAF item type" }
        val partial = File(destinationParent, ".${target.name}.shovel-partial")
        try {
            var written = 0L
            val input = resolver.openInputStream(source.uri)?.buffered()
                ?: throw IllegalStateException("The SAF file cannot be read")
            input.use { sourceStream ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        operation?.checkpoint()
                        val read = sourceStream.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written = Math.addExact(written, read.toLong())
                        operation?.progress(byteDelta = read.toLong(), currentName = safeName)
                    }
                }
            }
            val declared = source.length()
            if (declared > 0) require(written == declared) { "The local copy size does not match" }
            runCatching {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(partial.toPath(), target.toPath())
            }
            operation?.progress(itemDelta = 1, currentName = safeName)
        } finally {
            if (partial.exists()) partial.delete()
        }
        return target
    }

    private fun freeDocumentName(parent: DocumentFile, requested: String): String {
        if (parent.findFile(requested) == null) return requested
        val dot = requested.lastIndexOf('.').takeIf { it > 0 } ?: requested.length
        val stem = requested.substring(0, dot)
        val extension = requested.substring(dot)
        for (number in 2..10_000) {
            val candidate = "$stem ($number)$extension"
            if (parent.findFile(candidate) == null) return candidate
        }
        throw IllegalStateException("Could not choose an available SAF name")
    }

    private fun countCopyNode(counter: CopyCounter, depth: Int) {
        require(depth <= MAX_DEPTH) { "The copied-folder depth limit was exceeded" }
        counter.entries += 1
        require(counter.entries <= MAX_COPY_ENTRIES) { "The copied-item limit was exceeded" }
    }

    private fun writeLocations(locations: List<SafLocation>) {
        val array = JSONArray()
        locations.forEach { location ->
            array.put(
                JSONObject()
                    .put("uri", location.uri)
                    .put("title", location.title)
                    .put("customTitle", location.customTitle ?: JSONObject.NULL)
                    .put("providerPackage", location.providerPackageName ?: JSONObject.NULL)
                    .put("providerLabel", location.providerLabel ?: JSONObject.NULL)
                    .put("folderName", location.folderName ?: JSONObject.NULL),
            )
        }
        check(preferences.edit().putString(KEY_LOCATIONS, array.toString()).commit()) { "Could not save the location" }
    }

    private class CopyCounter(var entries: Int = 0)
}
