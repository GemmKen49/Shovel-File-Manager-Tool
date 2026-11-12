package com.shovel.filemanager.advanced

import android.content.Context
import android.os.Environment
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.data.FileEntryOrdering
import com.shovel.filemanager.data.FileSelectionSummary
import com.shovel.filemanager.editing.EditConflict
import com.shovel.filemanager.editing.EditLimits
import com.shovel.filemanager.editing.EditOrigin
import com.shovel.filemanager.editing.EditSaveResult
import com.shovel.filemanager.editing.EditSession
import com.shovel.filemanager.editing.FileRevision
import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.model.SortDirection
import com.shovel.filemanager.model.SortMode
import com.shovel.filemanager.operations.OperationContext
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

data class PrivilegedRoot(
    val path: String,
    val title: String,
)

data class PrivilegedTransferResult(
    val copiedRoots: Int,
    val skippedRoots: Int,
)

class PrivilegedFileRepository(
    context: Context,
    private val access: AdvancedAccessManager,
) {
    companion object {
        private const val COPY_BUFFER = 256 * 1_024
        private const val MAX_KEEP_BOTH_ATTEMPTS = 10_000
    }

    private val previewCache = PrivilegedPreviewCache(context.cacheDir)
    private val primaryStorage = Environment.getExternalStorageDirectory().absolutePath
    private val protectedRoots: List<PrivilegedRoot> = listOf(
        PrivilegedRoot("$primaryStorage/Android/data", "Android/data"),
        PrivilegedRoot("$primaryStorage/Android/obb", "Android/obb"),
    )
    val roots: List<PrivilegedRoot>
        get() = when (access.state.value.activeBackend) {
            AdvancedAccessBackend.ROOT, AdvancedAccessBackend.SHIZUKU_ROOT -> listOf(PrivilegedRoot("/", "Root"))
            else -> protectedRoots
        }
    private val allowedRoots: List<String>
        get() = roots.map(PrivilegedRoot::path)

    suspend fun probeAndroidData(): Result<Boolean> = ioResult {
        val manager = access.fileSystemOrThrow()
        val root = manager.getFile(protectedRoots.first().path)
        val accessible = root.isDirectory && root.list() != null
        access.reportAndroidDataProbe(accessible)
        accessible
    }

    suspend fun availableRoots(): Result<List<PrivilegedRoot>> = ioResult {
        val manager = access.fileSystemOrThrow()
        roots.filter { root -> runCatching { manager.getFile(root.path).isDirectory }.getOrDefault(false) }
    }

    suspend fun list(
        directoryPath: String,
        includeHidden: Boolean,
        sortMode: SortMode,
        sortDirection: SortDirection,
    ): Result<List<FileEntry>> = ioResult {
        val manager = access.fileSystemOrThrow()
        val directory = existingContained(manager, directoryPath, allowRoot = true)
        require(directory.isDirectory) { "This is not a folder" }
        val children = directory.listFiles() ?: throw SecurityException("Could not read the folder contents")
        require(children.size <= PrivilegedPathRules.MAX_VISIBLE_ENTRIES) {
            "The folder has more than ${PrivilegedPathRules.MAX_VISIBLE_ENTRIES} items"
        }
        val entries = children.asSequence()
            .filter { includeHidden || !it.isHidden }
            .filter { child -> runCatching { canonicalContained(child, allowRoot = false); true }.getOrDefault(false) }
            .map(::toEntry)
            .toList()
        FileEntryOrdering.order(entries, sortMode, sortDirection)
    }

    suspend fun createDirectory(parentPath: String, requestedName: String): Result<FileEntry> = ioResult {
        val manager = access.fileSystemOrThrow()
        val parent = existingContained(manager, parentPath, allowRoot = true)
        require(parent.isDirectory) { "The parent folder is unavailable" }
        val path = PrivilegedPathRules.child(parent.canonicalPath, requestedName, allowedRoots)
        val target = manager.getFile(path)
        require(!target.exists()) { "That name is already in use" }
        check(target.mkdir()) { "Could not create the folder" }
        toEntry(existingContained(manager, path, allowRoot = false))
    }

    suspend fun selectionInfo(paths: Collection<String>): Result<FileSelectionSummary> = ioResult {
        val selected = paths.map(String::trim).filter(String::isNotEmpty).distinct()
        require(selected.isNotEmpty() && selected.size <= PrivilegedPathRules.MAX_SELECTED_ROOTS) {
            "Invalid number of selected items"
        }
        val manager = access.fileSystemOrThrow()
        data class Pending(val file: ExtendedFile, val depth: Int, val selectedRoot: Boolean)
        val pending = ArrayDeque<Pending>()
        selected.asReversed().forEach { pending.add(Pending(existingContained(manager, it, allowRoot = false), 0, true)) }
        val seen = hashSetOf<String>()
        var files = 0
        var folders = 0
        var bytes = 0L
        var scanned = 0
        var complete = true
        while (pending.isNotEmpty()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (scanned >= 100_000) { complete = false; break }
            val current = pending.removeLast()
            val canonical = runCatching { canonicalContained(current.file, allowRoot = false) }.getOrElse {
                complete = false; continue
            }
            if (!seen.add(canonical.canonicalPath)) { complete = false; continue }
            scanned++
            when {
                runCatching { Files.isSymbolicLink(canonical.toPath()) }.getOrDefault(false) -> complete = false
                canonical.isDirectory -> {
                    if (!current.selectedRoot) folders++
                    if (current.depth >= 64) { complete = false; continue }
                    val children = canonical.listFiles()
                    if (children == null) complete = false else children.take(50_000).asReversed().forEach {
                        pending.add(Pending(it, current.depth + 1, false))
                    }
                }
                canonical.isFile -> {
                    files++
                    val value = canonical.length().coerceAtLeast(0L)
                    bytes = if (value > Long.MAX_VALUE - bytes) Long.MAX_VALUE else bytes + value
                }
                else -> complete = false
            }
        }
        FileSelectionSummary(selected.size, files, folders, bytes, scanned, complete)
    }

    suspend fun createFile(parentPath: String, requestedName: String): Result<FileEntry> = ioResult {
        val manager = access.fileSystemOrThrow()
        val parent = existingContained(manager, parentPath, allowRoot = true)
        require(parent.isDirectory) { "The parent folder is unavailable" }
        val path = PrivilegedPathRules.child(parent.canonicalPath, requestedName, allowedRoots)
        val target = manager.getFile(path)
        require(!target.exists()) { "That name is already in use" }
        check(target.createNewFile()) { "Could not create the file" }
        toEntry(existingContained(manager, path, allowRoot = false))
    }

    suspend fun rename(path: String, requestedName: String): Result<FileEntry> = ioResult {
        val manager = access.fileSystemOrThrow()
        val source = existingContained(manager, path, allowRoot = false)
        val parent = requireNotNull(source.parentFile) { "The root folder cannot be renamed" }
        canonicalContained(parent, allowRoot = true)
        val targetPath = PrivilegedPathRules.child(parent.canonicalPath, requestedName, allowedRoots)
        val target = manager.getFile(targetPath)
        require(!target.exists()) { "That name is already in use" }
        check(source.renameTo(target)) { "Could not rename" }
        toEntry(existingContained(manager, targetPath, allowRoot = false))
    }

    suspend fun deletePermanently(paths: List<String>, operation: OperationContext): PrivilegedTransferResult = withContext(Dispatchers.IO) {
        require(paths.isNotEmpty() && paths.size <= PrivilegedPathRules.MAX_SELECTED_ROOTS) { "Invalid number of selected items" }
        val manager = access.fileSystemOrThrow()
        val sources = paths.distinct().map { existingContained(manager, it, allowRoot = false) }
        val summary = selectionInfo(paths).getOrThrow()
        operation.setTotals(
            items = summary.scannedNodes.takeIf { summary.complete },
            bytes = summary.totalBytes.takeIf { summary.complete },
        )
        var removed = 0
        sources.forEach { source ->
            operation.checkpoint()
            deleteTree(source, source, operation, depth = 0, counter = Counter())
            removed += 1
        }
        PrivilegedTransferResult(removed, 0)
    }

    suspend fun copyWithin(
        sourcePaths: List<String>,
        destinationPath: String,
        move: Boolean,
        conflictPolicy: ConflictPolicy,
        operation: OperationContext,
    ): PrivilegedTransferResult = withContext(Dispatchers.IO) {
        require(sourcePaths.isNotEmpty() && sourcePaths.size <= PrivilegedPathRules.MAX_SELECTED_ROOTS) { "Invalid number of selected items" }
        val manager = access.fileSystemOrThrow()
        val destination = existingContained(manager, destinationPath, allowRoot = true)
        require(destination.isDirectory) { "The destination folder is unavailable" }
        val sources = sourcePaths.distinct().map { existingContained(manager, it, allowRoot = false) }
        require(sources.none { source -> destination.canonicalPath == source.canonicalPath || destination.canonicalPath.startsWith("${source.canonicalPath}/") }) {
            "A folder cannot be copied into itself"
        }
        operation.setTotals(null, null)
        var copied = 0
        var skipped = 0
        sources.forEach { source ->
            operation.checkpoint()
            val target = resolveRemoteTarget(destination, source.name, conflictPolicy)
            if (target == null) {
                skipped += 1
                return@forEach
            }
            if (move && !target.exists() && source.renameTo(target)) {
                copied += 1
                operation.progress(itemDelta = 1, currentName = source.name)
                return@forEach
            }
            val partial = uniqueRemoteChild(destination, ".${target.name}.shovel-partial")
            try {
                copyRemoteTree(source, partial, operation, depth = 0, counter = Counter())
                require(equalTree(source, partial, Counter())) { "The copied content was not verified" }
                if (target.exists()) deleteTree(target, target, operation, 0, Counter())
                require(partial.renameTo(target)) { "Could not finish the copy" }
                if (move) deleteTree(source, source, operation, 0, Counter())
                copied += 1
                operation.progress(itemDelta = 1, currentName = source.name)
            } finally {
                if (partial.exists()) runCatching { deleteTree(partial, partial, OperationContext.background(), 0, Counter()) }
            }
        }
        PrivilegedTransferResult(copied, skipped)
    }

    suspend fun copyFromLocal(
        sourcePaths: List<String>,
        destinationPath: String,
        move: Boolean,
        conflictPolicy: ConflictPolicy,
        operation: OperationContext,
    ): PrivilegedTransferResult = withContext(Dispatchers.IO) {
        require(sourcePaths.isNotEmpty() && sourcePaths.size <= PrivilegedPathRules.MAX_SELECTED_ROOTS) { "Invalid number of selected items" }
        val manager = access.fileSystemOrThrow()
        val destination = existingContained(manager, destinationPath, allowRoot = true)
        val sources = sourcePaths.distinct().map { File(it).canonicalFile }
        require(sources.all(File::exists)) { "The local source no longer exists" }
        var copied = 0
        var skipped = 0
        operation.setTotals(null, null)
        sources.forEach { source ->
            operation.checkpoint()
            val target = resolveRemoteTarget(destination, source.name, conflictPolicy)
            if (target == null) {
                skipped += 1
                return@forEach
            }
            val partial = uniqueRemoteChild(destination, ".${target.name}.shovel-partial")
            try {
                copyLocalTree(source, partial, operation, 0, Counter())
                require(equalLocalRemoteTree(source, partial, Counter())) { "The copied content was not verified" }
                if (target.exists()) deleteTree(target, target, operation, 0, Counter())
                require(partial.renameTo(target)) { "Could not finish the copy" }
                if (move) deleteLocalTree(source, source, operation, 0, Counter())
                copied += 1
                operation.progress(itemDelta = 1, currentName = source.name)
            } finally {
                if (partial.exists()) runCatching { deleteTree(partial, partial, OperationContext.background(), 0, Counter()) }
            }
        }
        PrivilegedTransferResult(copied, skipped)
    }

    suspend fun copyToLocal(
        sourcePaths: List<String>,
        destinationDirectory: File,
        move: Boolean,
        conflictPolicy: ConflictPolicy,
        operation: OperationContext,
    ): PrivilegedTransferResult = withContext(Dispatchers.IO) {
        require(sourcePaths.isNotEmpty() && sourcePaths.size <= PrivilegedPathRules.MAX_SELECTED_ROOTS) { "Invalid number of selected items" }
        val manager = access.fileSystemOrThrow()
        val destination = destinationDirectory.canonicalFile
        require(destination.isDirectory && destination.canWrite()) { "The local destination folder is not writable" }
        val sources = sourcePaths.distinct().map { existingContained(manager, it, allowRoot = false) }
        var copied = 0
        var skipped = 0
        operation.setTotals(null, null)
        sources.forEach { source ->
            operation.checkpoint()
            val target = resolveLocalTarget(destination, source.name, conflictPolicy)
            if (target == null) {
                skipped += 1
                return@forEach
            }
            val partial = File(destination, ".${target.name}.${UUID.randomUUID()}.shovel-partial")
            require(partial.canonicalFile.parentFile == destination) { "The temporary path escapes the destination" }
            try {
                copyRemoteToLocalTree(source, partial, operation, 0, Counter())
                require(equalRemoteLocalTree(source, partial, Counter())) { "The copied content was not verified" }
                if (target.exists()) deleteLocalTree(target, target, operation, 0, Counter())
                require(partial.renameTo(target)) { "Could not finish the copy" }
                if (move) deleteTree(source, source, operation, 0, Counter())
                copied += 1
                operation.progress(itemDelta = 1, currentName = source.name)
            } finally {
                if (partial.exists()) partial.deleteRecursively()
            }
        }
        PrivilegedTransferResult(copied, skipped)
    }

    suspend fun stageForPreview(entry: FileEntry): Result<File> = ioResult {
        require(!entry.isDirectory && entry.sizeBytes <= EditLimits.MAX_FILE_BYTES) { "The file is too large to preview" }
        // Readable system files still need a private copy: the FileProvider must
        // not expose the whole device root just to play an alarm or open a PDF.
        val local = File(entry.absolutePath).takeIf { it.isFile && it.canRead() }
        val protected = if (local == null) {
            existingContained(access.fileSystemOrThrow(), entry.absolutePath, allowRoot = false)
                .also { require(it.isFile) { "File unavailable" } }
        } else null
        val destination = previewCache.createDestination(entry)
        try {
            (local?.inputStream() ?: requireNotNull(protected).newInputStream()).buffered().use { input ->
                FileOutputStream(destination).buffered().use { output -> copyBounded(input, output, EditLimits.MAX_FILE_BYTES, null) }
            }
            require(destination.length() == (local?.length() ?: requireNotNull(protected).length())) { "The preview copy size does not match" }
            previewCache.validateCompleted(destination)
            destination
        } catch (error: Throwable) {
            previewCache.discard(destination)
            throw error
        }
    }

    fun discardPreview(file: File?): Boolean = previewCache.discard(file)

    fun openInput(path: String): InputStream {
        val manager = access.fileSystemOrThrow()
        return existingContained(manager, path, allowRoot = false).newInputStream()
    }

    fun saveOrigin(session: EditSession, force: Boolean): EditSaveResult {
        val origin = session.origin as? EditOrigin.Privileged ?: error("The editing session is not privileged")
        val manager = access.fileSystemOrThrow()
        val target = manager.getFile(PrivilegedPathRules.requireWithinAllowed(origin.path, allowedRoots, allowRoot = false))
        val current = revisionOrNull(target)
        if (!force && !session.originRevision.hasSameContent(current)) {
            return EditSaveResult.Conflict(EditConflict(origin.label, session.originRevision, current))
        }
        require(origin.canWrite) { "The original file is not writable; use Save as" }
        val parent = requireNotNull(target.parentFile)
        canonicalContained(parent, allowRoot = true)
        val temporary = uniqueRemoteChild(parent, ".${target.name}.shovel-edit")
        val backup = uniqueRemoteChild(parent, ".${target.name}.shovel-backup")
        var backupMade = false
        try {
            writeRemoteFile(temporary, session.workingFile.inputStream(), EditLimits.MAX_FILE_BYTES)
            val temporaryRevision = requireNotNull(revisionOrNull(temporary)) { "Could not verify the temporary copy" }
            require(session.workingRevision.hasSameContent(temporaryRevision)) { "The temporary copy does not match the edited file" }
            if (!force) {
                val latest = revisionOrNull(target)
                if (!session.originRevision.hasSameContent(latest)) {
                    return EditSaveResult.Conflict(EditConflict(origin.label, session.originRevision, latest))
                }
            }
            if (target.exists()) {
                require(target.renameTo(backup)) { "Could not create a backup of the original file" }
                backupMade = true
            }
            require(temporary.renameTo(target)) { "Could not write the edited file" }
            val verified = requireNotNull(revisionOrNull(target)) { "The saved file could not be verified" }
            require(session.workingRevision.hasSameContent(verified)) { "The saved file contents do not match" }
            if (backupMade) require(backup.delete()) { "The file was saved, but its temporary backup could not be removed" }
            return EditSaveResult.Saved(verified)
        } catch (error: Throwable) {
            if (backupMade && !target.exists()) runCatching { backup.renameTo(target) }
            throw error
        } finally {
            if (temporary.exists()) runCatching { temporary.delete() }
            if (backup.exists() && target.exists()) runCatching { backup.delete() }
        }
    }

    private fun toEntry(file: ExtendedFile): FileEntry = FileEntry(
        absolutePath = file.absolutePath,
        name = file.name.ifBlank { file.absolutePath },
        kind = FileSystemRules.detectKind(file.name, mimeType = null, isDirectory = file.isDirectory),
        sizeBytes = if (file.isFile) file.length().coerceAtLeast(0) else 0L,
        modifiedAtMillis = file.lastModified().coerceAtLeast(0),
        isHidden = file.isHidden,
        isReadable = file.canRead(),
        isWritable = file.canWrite(),
    )

    private fun existingContained(manager: FileSystemManager, path: String, allowRoot: Boolean): ExtendedFile {
        val normalized = PrivilegedPathRules.requireWithinAllowed(path, allowedRoots, allowRoot)
        val file = manager.getFile(normalized)
        require(file.exists()) { "The file or folder no longer exists" }
        require(!file.isSymlink) { "Symbolic links are not opened in this mode" }
        return canonicalContained(file, allowRoot)
    }

    private fun canonicalContained(file: ExtendedFile, allowRoot: Boolean): ExtendedFile {
        val canonical = file.canonicalFile
        PrivilegedPathRules.requireWithinAllowed(canonical.absolutePath, allowedRoots, allowRoot)
        return canonical
    }

    private fun resolveRemoteTarget(parent: ExtendedFile, name: String, policy: ConflictPolicy): ExtendedFile? {
        val requested = parent.getChildFile(FileSystemRules.validateFileName(name).getOrThrow())
        PrivilegedPathRules.requireWithinAllowed(requested.absolutePath, allowedRoots, allowRoot = false)
        require(!requested.exists() || !requested.isSymlink) { "Symbolic links cannot be overwritten" }
        if (requested.exists()) canonicalContained(requested, allowRoot = false)
        if (!requested.exists()) return requested
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.ASK -> throw IllegalStateException("Paskirtyje jau yra „$name“")
            ConflictPolicy.KEEP_BOTH -> keepBothRemote(parent, name)
            ConflictPolicy.REPLACE, ConflictPolicy.MERGE -> requested
        }
    }

    private fun resolveLocalTarget(parent: File, name: String, policy: ConflictPolicy): File? {
        val safeName = FileSystemRules.validateFileName(name).getOrThrow()
        val requestedRaw = File(parent, safeName)
        require(!Files.isSymbolicLink(requestedRaw.toPath())) { "Symbolic links cannot be overwritten" }
        val requested = requestedRaw.canonicalFile
        require(requested.parentFile == parent) { "The destination path escapes the folder" }
        if (!requested.exists()) return requested
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.ASK -> throw IllegalStateException("Paskirtyje jau yra „$name“")
            ConflictPolicy.KEEP_BOTH -> FileSystemRules.keepBothTarget(requested)
            ConflictPolicy.REPLACE, ConflictPolicy.MERGE -> requested
        }
    }

    private fun keepBothRemote(parent: ExtendedFile, name: String): ExtendedFile {
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = dot?.let { name.substring(0, it) } ?: name
        val extension = dot?.let { name.substring(it) }.orEmpty()
        for (index in 1..MAX_KEEP_BOTH_ATTEMPTS) {
            val candidate = parent.getChildFile("$base ($index)$extension")
            if (!candidate.exists()) return candidate
        }
        error("Could not find an available name")
    }

    private fun uniqueRemoteChild(parent: ExtendedFile, prefix: String): ExtendedFile {
        repeat(32) {
            val safePrefix = prefix.take(120).replace('/', '_')
            val candidate = parent.getChildFile("$safePrefix-${UUID.randomUUID()}")
            PrivilegedPathRules.requireWithinAllowed(candidate.absolutePath, allowedRoots, allowRoot = false)
            if (!candidate.exists()) return candidate
        }
        error("Could not choose a safe temporary name")
    }

    private suspend fun copyRemoteTree(source: ExtendedFile, target: ExtendedFile, operation: OperationContext, depth: Int, counter: Counter) {
        visit(source, depth, counter, operation)
        if (source.isSymlink) throw SecurityException("Symbolic links are not copied")
        if (source.isDirectory) {
            require(target.mkdir()) { "Could not create the folder copy" }
            source.listFiles()?.forEach { child -> copyRemoteTree(child, target.getChildFile(child.name), operation, depth + 1, counter) }
                ?: throw SecurityException("The folder is not readable")
        } else {
            writeRemoteFile(target, source.newInputStream(), source.length().coerceAtLeast(0))
            operation.progress(byteDelta = source.length().coerceAtLeast(0), currentName = source.name)
        }
    }

    private suspend fun copyLocalTree(source: File, target: ExtendedFile, operation: OperationContext, depth: Int, counter: Counter) {
        require(depth <= PrivilegedPathRules.MAX_DEPTH) { "The folder tree is too deep" }
        operation.checkpoint()
        counter.add()
        require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links are not copied" }
        if (source.isDirectory) {
            require(target.mkdir()) { "Could not create the folder copy" }
            source.listFiles()?.forEach { child -> copyLocalTree(child, target.getChildFile(child.name), operation, depth + 1, counter) }
                ?: throw SecurityException("The local folder is not readable")
        } else {
            writeRemoteFile(target, source.inputStream(), source.length().coerceAtLeast(0))
            operation.progress(byteDelta = source.length().coerceAtLeast(0), currentName = source.name)
        }
    }

    private suspend fun copyRemoteToLocalTree(source: ExtendedFile, target: File, operation: OperationContext, depth: Int, counter: Counter) {
        visit(source, depth, counter, operation)
        if (source.isSymlink) throw SecurityException("Symbolic links are not copied")
        if (source.isDirectory) {
            require(target.mkdir()) { "Could not create the local folder" }
            source.listFiles()?.forEach { child -> copyRemoteToLocalTree(child, File(target, child.name), operation, depth + 1, counter) }
                ?: throw SecurityException("The folder is not readable")
        } else {
            FileOutputStream(target).use { fileOutput ->
                source.newInputStream().buffered().use { input -> copyBounded(input, fileOutput, source.length().coerceAtLeast(0), operation) }
                fileOutput.fd.sync()
            }
        }
    }

    private fun writeRemoteFile(target: ExtendedFile, source: InputStream, maximumBytes: Long) {
        target.newOutputStream().buffered().use { output ->
            source.buffered().use { input -> copyBounded(input, output, maximumBytes, null) }
            output.flush()
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, maximumBytes: Long, operation: OperationContext?): Long {
        var total = 0L
        val buffer = ByteArray(COPY_BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total = Math.addExact(total, read.toLong())
            require(total <= maximumBytes) { "The file changed or exceeded the allowed size" }
            output.write(buffer, 0, read)
            operation?.progress(byteDelta = read.toLong())
        }
        return total
    }

    private suspend fun visit(file: ExtendedFile, depth: Int, counter: Counter, operation: OperationContext) {
        require(depth <= PrivilegedPathRules.MAX_DEPTH) { "The folder tree is too deep" }
        operation.checkpoint()
        canonicalContained(file, allowRoot = false)
        counter.add()
    }

    private suspend fun deleteTree(root: ExtendedFile, file: ExtendedFile, operation: OperationContext, depth: Int, counter: Counter) {
        visit(file, depth, counter, operation)
        require(file.canonicalPath == root.canonicalPath || file.canonicalPath.startsWith("${root.canonicalPath}/")) { "The deletion path escapes the selected item" }
        if (file.isSymlink) {
            require(file.delete()) { "Could not remove the symbolic link" }
            operation.progress(itemDelta = 1, currentName = file.name)
            return
        }
        if (file.isDirectory) file.listFiles()?.forEach { child -> deleteTree(root, child, operation, depth + 1, counter) }
            ?: throw SecurityException("The folder is not readable")
        val bytes = if (file.isFile) file.length().coerceAtLeast(0L) else 0L
        require(file.delete()) { "Could not remove: ${file.name}" }
        operation.progress(itemDelta = 1, byteDelta = bytes, currentName = file.name)
    }

    private suspend fun deleteLocalTree(root: File, file: File, operation: OperationContext, depth: Int, counter: Counter) {
        require(depth <= PrivilegedPathRules.MAX_DEPTH) { "The folder tree is too deep" }
        operation.checkpoint()
        counter.add()
        require(!Files.isSymbolicLink(file.toPath())) { "Symbolic links are not removed during a move" }
        val rootPath = root.canonicalFile.toPath()
        val path = file.canonicalFile.toPath()
        require(path.startsWith(rootPath)) { "The deletion path escapes the selected item" }
        if (file.isDirectory) file.listFiles()?.forEach { child -> deleteLocalTree(root, child, operation, depth + 1, counter) }
            ?: throw SecurityException("The local folder is not readable")
        require(file.delete()) { "Could not remove: ${file.name}" }
    }

    private fun equalTree(left: ExtendedFile, right: ExtendedFile, counter: Counter): Boolean {
        counter.add()
        if (left.isDirectory != right.isDirectory || left.isFile != right.isFile) return false
        if (left.isFile) return left.length() == right.length()
        val leftChildren = left.listFiles()?.associateBy { it.name } ?: return false
        val rightChildren = right.listFiles()?.associateBy { it.name } ?: return false
        return leftChildren.keys == rightChildren.keys && leftChildren.all { (name, child) -> equalTree(child, requireNotNull(rightChildren[name]), counter) }
    }

    private fun equalLocalRemoteTree(left: File, right: ExtendedFile, counter: Counter): Boolean {
        counter.add()
        if (left.isDirectory != right.isDirectory || left.isFile != right.isFile) return false
        if (left.isFile) return left.length() == right.length()
        val leftChildren = left.listFiles()?.associateBy { it.name } ?: return false
        val rightChildren = right.listFiles()?.associateBy { it.name } ?: return false
        return leftChildren.keys == rightChildren.keys && leftChildren.all { (name, child) -> equalLocalRemoteTree(child, requireNotNull(rightChildren[name]), counter) }
    }

    private fun equalRemoteLocalTree(left: ExtendedFile, right: File, counter: Counter): Boolean {
        counter.add()
        if (left.isDirectory != right.isDirectory || left.isFile != right.isFile) return false
        if (left.isFile) return left.length() == right.length()
        val leftChildren = left.listFiles()?.associateBy { it.name } ?: return false
        val rightChildren = right.listFiles()?.associateBy { it.name } ?: return false
        return leftChildren.keys == rightChildren.keys && leftChildren.all { (name, child) -> equalRemoteLocalTree(child, requireNotNull(rightChildren[name]), counter) }
    }

    private fun revisionOrNull(file: ExtendedFile): FileRevision? {
        if (!file.exists()) return null
        require(file.isFile) { "The original path is not a file" }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        file.newInputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                size = Math.addExact(size, read.toLong())
                require(size <= EditLimits.MAX_FILE_BYTES) { "The file is too large for edit verification" }
                digest.update(buffer, 0, read)
            }
        }
        return FileRevision(
            sizeBytes = size,
            modifiedAtMillis = file.lastModified().takeIf { it > 0 },
            sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
        )
    }

    private suspend fun <T> ioResult(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            access.reportOperationFailure(error)
            Result.failure(error)
        }
    }

    private class Counter {
        private var value = 0
        fun add() {
            value = Math.addExact(value, 1)
            require(value <= PrivilegedPathRules.MAX_TREE_ENTRIES) { "The file tree exceeded the safety limit" }
        }
    }
}
