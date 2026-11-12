package com.shovel.filemanager.operations

import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.model.ConflictPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LocalFileOperator(private val onMutation: suspend (List<File>) -> Unit = {}) {
    companion object {
        const val MAX_OPERATION_ENTRIES = 200_000
        const val MAX_TREE_DEPTH = 64
        private const val BUFFER_SIZE = 1 * 1_024 * 1_024
    }

    private data class Scan(val items: Int, val bytes: Long)

    suspend fun copyOrMove(
        sourcePaths: List<String>,
        destinationDirectoryPath: String,
        move: Boolean,
        conflictPolicy: ConflictPolicy,
        context: OperationContext,
    ) = withContext(Dispatchers.IO) {
        require(sourcePaths.isNotEmpty()) { "No files selected" }
        val destination = File(destinationDirectoryPath).canonicalFile
        require(destination.isDirectory) { "The destination folder is unavailable" }

        val sources = sourcePaths.distinct().map { File(it).canonicalFile }
        sources.forEach { source ->
            require(source.exists()) { "The file no longer exists: ${source.name}" }
            require(source != destination) { "A folder cannot be copied onto itself" }
            require(!source.isDirectory || !FileSystemRules.isContained(source, destination)) {
                "A folder cannot be copied into its own subfolder"
            }
        }

        val totals = sources.fold(Scan(0, 0)) { total, source ->
            val scan = scan(source)
            Scan(Math.addExact(total.items, scan.items), Math.addExact(total.bytes, scan.bytes))
        }
        require(totals.items <= MAX_OPERATION_ENTRIES) { "The operation exceeds the limit of $MAX_OPERATION_ENTRIES items" }
        context.setTotals(totals.items, totals.bytes)

        sources.forEach { source ->
            context.checkpoint()
            val initialTarget = File(destination, source.name)
            require(initialTarget.canonicalFile != source) { "Source and destination are the same file" }
            val target = resolveTarget(initialTarget, source.isDirectory, conflictPolicy) ?: return@forEach

            val changed = mutableListOf<File>()
            publishingStorageChanges(onMutation, { changed }) {
            if (move && !target.exists() && source.renameTo(target)) {
                changed += source
                changed += target
                val movedScan = scan(target)
                context.progress(movedScan.items, movedScan.bytes, source.name)
            } else {
                val fullyCopied = copyRecursively(source, target, conflictPolicy, context, changed, depth = 0)
                if (move && fullyCopied) {
                    deleteRecursively(source, context = null, depth = 0, changed = changed)
                }
            }
            }
        }
    }

    suspend fun deletePermanently(paths: List<String>, context: OperationContext) = withContext(Dispatchers.IO) {
        val sources = paths.distinct().map { File(it).canonicalFile }
        val total = sources.fold(Scan(0, 0)) { sum, source ->
            val scan = scan(source)
            Scan(Math.addExact(sum.items, scan.items), Math.addExact(sum.bytes, scan.bytes))
        }
        context.setTotals(total.items, total.bytes)
        sources.forEach {
            val changed = mutableListOf<File>()
            publishingStorageChanges(onMutation, { changed }) {
                deleteRecursively(it, context, depth = 0, changed = changed)
            }
        }
    }

    private suspend fun copyRecursively(
        source: File,
        requestedTarget: File,
        conflictPolicy: ConflictPolicy,
        context: OperationContext,
        changed: MutableList<File>,
        depth: Int,
    ): Boolean {
        require(depth <= MAX_TREE_DEPTH) { "Folder depth exceeds the limit of $MAX_TREE_DEPTH" }
        context.checkpoint()
        val target = resolveTarget(requestedTarget, source.isDirectory, conflictPolicy) ?: return false

        if (source.isDirectory) {
            if (!target.exists()) check(target.mkdir()) { "Could not create ${target.name}" }
            val children = source.listFiles() ?: throw SecurityException("Could not read ${source.name}")
            var fullyCopied = true
            children.forEach { child ->
                if (!copyRecursively(child, File(target, child.name), conflictPolicy, context, changed, depth + 1)) fullyCopied = false
            }
            target.setLastModified(source.lastModified())
            context.progress(itemDelta = 1, currentName = source.name)
            return fullyCopied
        }

        val partial = File(target.parentFile, ".${target.name}.${System.nanoTime()}.partial")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        context.checkpoint()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        context.progress(byteDelta = read.toLong(), currentName = source.name)
                    }
                    output.fd.sync()
                }
            }
            require(partial.length() == source.length()) { "The copy size does not match: ${source.name}" }
            if (target.exists() && conflictPolicy != ConflictPolicy.REPLACE) throw FileAlreadyExistsException(target)
            if (conflictPolicy == ConflictPolicy.REPLACE) {
                try {
                    Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else Files.move(partial.toPath(), target.toPath())
            changed += target
            target.setLastModified(source.lastModified())
            context.progress(itemDelta = 1, currentName = source.name)
        } finally {
            if (partial.exists()) partial.delete()
        }
        return true
    }

    private suspend fun deleteRecursively(file: File, context: OperationContext?, depth: Int, changed: MutableList<File>) {
        require(depth <= MAX_TREE_DEPTH) { "Folder depth exceeds the limit of $MAX_TREE_DEPTH" }
        context?.checkpoint()
        if (file.isDirectory) {
            val children = file.listFiles() ?: throw SecurityException("Could not read ${file.name}")
            children.forEach { deleteRecursively(it, context, depth + 1, changed) }
        }
        val regularFile = file.isFile
        val size = if (regularFile) file.length() else 0
        if (!file.delete()) throw IllegalStateException("Could not delete ${file.name}")
        if (regularFile) changed += file
        context?.progress(itemDelta = 1, byteDelta = size, currentName = file.name)
    }

    private fun scan(root: File): Scan {
        var items = 0
        var bytes = 0L
        val pending = ArrayDeque<Pair<File, Int>>()
        pending.add(root to 0)
        while (pending.isNotEmpty()) {
            val (current, depth) = pending.removeLast()
            require(depth <= MAX_TREE_DEPTH) { "Folder depth exceeds the limit of $MAX_TREE_DEPTH" }
            items = Math.addExact(items, 1)
            require(items <= MAX_OPERATION_ENTRIES) { "Too many items" }
            if (current.isDirectory) {
                current.listFiles()?.forEach { pending.add(it to depth + 1) }
                    ?: throw SecurityException("Could not read ${current.name}")
            } else {
                bytes = Math.addExact(bytes, current.length().coerceAtLeast(0))
            }
        }
        return Scan(items, bytes)
    }

    private fun resolveTarget(target: File, sourceIsDirectory: Boolean, policy: ConflictPolicy): File? {
        if (!target.exists()) return target
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.KEEP_BOTH -> FileSystemRules.keepBothTarget(target)
            ConflictPolicy.REPLACE -> {
                require(target.isDirectory == sourceIsDirectory) { "Could not replace ${target.name}" }
                target // The old file stays intact until the replacement copy is complete.
            }
            ConflictPolicy.MERGE -> {
                require(sourceIsDirectory && target.isDirectory) { "Only folders can be merged" }
                target
            }
            ConflictPolicy.ASK -> throw FileAlreadyExistsException(target)
        }
    }
}

class FileAlreadyExistsException(val target: File) : IllegalStateException("Already exists: ${target.name}")
