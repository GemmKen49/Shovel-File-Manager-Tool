package com.shovel.filemanager.operations

import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.model.ConflictPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

enum class TransferVerification { SIZE, SHA256 }
enum class TransferFailurePolicy { STOP, SKIP_AND_CONTINUE }
enum class TransferPhase { COPY, DELETE_SOURCES, FINALIZE_BACKUPS, COMPLETE }
enum class DurableTransferStatus { QUEUED, RUNNING, FAILED, COMPLETED, COMPLETED_WITH_ERRORS, CANCELLED, INTERRUPTED }

data class PlannedTransferItem(
    val index: Int,
    val sourceRootIndex: Int,
    val sourcePath: String,
    val targetPath: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val replaceExisting: Boolean,
)

data class DurableTransferPlan(
    val schemaVersion: Int = 1,
    val id: String,
    val createdAtMillis: Long,
    val destinationPath: String,
    val sourceRoots: List<String>,
    val move: Boolean,
    val conflictPolicy: ConflictPolicy,
    val verification: TransferVerification,
    val failurePolicy: TransferFailurePolicy,
    val items: List<PlannedTransferItem>,
    val totalBytes: Long,
)

data class DurableTransferState(
    val schemaVersion: Int = 1,
    val planId: String,
    val status: DurableTransferStatus = DurableTransferStatus.QUEUED,
    val phase: TransferPhase = TransferPhase.COPY,
    val nextItemIndex: Int = 0,
    val nextDeleteRootIndex: Int = 0,
    val nextFinalizeItemIndex: Int = 0,
    val failedItemIndices: List<Int> = emptyList(),
    val retryItemIndices: List<Int> = emptyList(),
    val retryPosition: Int = 0,
    val attempt: Int = 0,
    val lastMessage: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

interface DurableTransferStateWriter {
    fun saveState(state: DurableTransferState)
}

class DurableTransferPlanner {
    companion object {
        const val MAX_SOURCE_PATHS = 10_000
        const val MAX_PLAN_ITEMS = 200_000
        const val MAX_TREE_DEPTH = 64
    }

    private data class Pending(
        val sourceRootIndex: Int,
        val source: File,
        val requestedTarget: File,
        val depth: Int,
    )

    fun create(
        sourcePaths: List<String>,
        destinationDirectoryPath: String,
        move: Boolean,
        conflictPolicy: ConflictPolicy,
        verification: TransferVerification,
        failurePolicy: TransferFailurePolicy,
        id: String = UUID.randomUUID().toString(),
        createdAtMillis: Long = System.currentTimeMillis(),
    ): DurableTransferPlan {
        require(sourcePaths.isNotEmpty()) { "No files selected" }
        require(sourcePaths.size <= MAX_SOURCE_PATHS) { "More than $MAX_SOURCE_PATHS source paths were selected" }
        require(conflictPolicy != ConflictPolicy.ASK) { "Conflict handling must be selected before planning" }
        require(!move || failurePolicy == TransferFailurePolicy.STOP) {
            "A move must stop on error so the source is not removed partially"
        }

        val destination = File(destinationDirectoryPath).canonicalFile
        require(destination.isDirectory) { "The destination folder is unavailable" }
        val sources = sourcePaths.map { path ->
            val requested = File(path)
            require(!Files.isSymbolicLink(requested.toPath())) { "Symbolic links are not copied" }
            requested.canonicalFile
        }.distinct()
        sources.forEach { source ->
            require(source.exists()) { "The file no longer exists: ${source.name}" }
            require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links are not supported: ${source.name}" }
            require(source != destination) { "A folder cannot be copied onto itself" }
            require(!source.isDirectory || !FileSystemRules.isContained(source, destination)) {
                "A folder cannot be copied into its own subfolder"
            }
        }

        val reservedTargets = linkedSetOf<String>()
        val items = ArrayList<PlannedTransferItem>()
        var totalBytes = 0L
        sources.forEachIndexed { rootIndex, source ->
            val pending = ArrayDeque<Pending>()
            pending.add(Pending(rootIndex, source, File(destination, source.name), 0))
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                require(node.depth <= MAX_TREE_DEPTH) { "Folder depth exceeds the limit of $MAX_TREE_DEPTH" }
                require(!Files.isSymbolicLink(node.requestedTarget.toPath())) { "Symbolic links cannot be overwritten" }
                val resolved = resolveTarget(node.source, node.requestedTarget, conflictPolicy, reservedTargets) ?: continue
                val (target, replaceExisting) = resolved
                val targetKey = target.canonicalPath
                require(FileSystemRules.isContained(destination, target)) { "The target escapes the destination folder" }
                require(node.source.canonicalPath != targetKey) { "Source and destination are the same file" }
                require(reservedTargets.add(targetKey)) {
                    "Several sources target the same destination: ${target.name}; choose \"Keep both\""
                }

                val index = items.size
                require(index < MAX_PLAN_ITEMS) { "The operation exceeds the limit of $MAX_PLAN_ITEMS items" }
                val size = if (node.source.isFile) node.source.length().coerceAtLeast(0) else 0L
                totalBytes = Math.addExact(totalBytes, size)
                items += PlannedTransferItem(
                    index = index,
                    sourceRootIndex = node.sourceRootIndex,
                    sourcePath = node.source.canonicalPath,
                    targetPath = targetKey,
                    directory = node.source.isDirectory,
                    sizeBytes = size,
                    modifiedAtMillis = node.source.lastModified().coerceAtLeast(0),
                    replaceExisting = replaceExisting,
                )

                if (node.source.isDirectory) {
                    val children = node.source.listFiles()?.sortedBy(File::getName)
                        ?: throw SecurityException("Could not read ${node.source.name}")
                    children.asReversed().forEach { child ->
                        require(!Files.isSymbolicLink(child.toPath())) { "Symbolic links are not supported: ${child.name}" }
                        pending.add(Pending(node.sourceRootIndex, child, File(target, child.name), node.depth + 1))
                    }
                }
            }
        }
        require(items.isNotEmpty()) { "No items remain to copy under the selected conflict policy" }

        return DurableTransferPlan(
            id = id,
            createdAtMillis = createdAtMillis,
            destinationPath = destination.canonicalPath,
            sourceRoots = sources.map(File::getCanonicalPath),
            move = move,
            conflictPolicy = conflictPolicy,
            verification = verification,
            failurePolicy = failurePolicy,
            items = items,
            totalBytes = totalBytes,
        )
    }

    private fun resolveTarget(
        source: File,
        requested: File,
        policy: ConflictPolicy,
        reserved: Set<String>,
    ): Pair<File, Boolean>? {
        val canonical = requested.canonicalFile
        val occupied = canonical.exists() || canonical.canonicalPath in reserved
        if (!occupied) return canonical to false
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.KEEP_BOTH -> keepBothTarget(canonical, reserved) to false
            ConflictPolicy.REPLACE -> canonical to canonical.exists()
            ConflictPolicy.MERGE -> {
                require(source.isDirectory && canonical.isDirectory) { "Only folders can be merged: ${canonical.name}" }
                canonical to false
            }
            ConflictPolicy.ASK -> error("Conflict handling was not selected")
        }
    }

    private fun keepBothTarget(requested: File, reserved: Set<String>): File {
        val extension = requested.extension.takeIf(String::isNotBlank)
        val stem = if (extension == null) requested.name else requested.name.removeSuffix(".$extension")
        for (index in 1..9_999) {
            val name = if (extension == null) "$stem ($index)" else "$stem ($index).$extension"
            val candidate = File(requested.parentFile, name).canonicalFile
            if (!candidate.exists() && candidate.canonicalPath !in reserved) return candidate
        }
        throw IllegalStateException("Could not pick a unique name: ${requested.name}")
    }
}

class DurableTransferEngine {
    companion object {
        private const val BUFFER_SIZE = 1 * 1_024 * 1_024
        private const val MAX_RECORDED_FAILURES = 100
        private const val STATE_CHECKPOINT_ITEMS = 32
    }

    suspend fun execute(
        plan: DurableTransferPlan,
        initialState: DurableTransferState,
        stateWriter: DurableTransferStateWriter,
        context: OperationContext,
    ): DurableTransferState {
        validatePlanState(plan, initialState)
        var state = initialState.copy(
            status = DurableTransferStatus.RUNNING,
            attempt = Math.addExact(initialState.attempt, 1),
            lastMessage = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
        stateWriter.saveState(state)

        val retryItems = state.retryItemIndices
        val totalItems = if (retryItems.isEmpty()) plan.items.size * if (plan.move) 2 else 1 else retryItems.size
        val totalBytes = if (retryItems.isEmpty()) plan.totalBytes else retryItems.sumOf { plan.items[it].sizeBytes }
        context.setTotals(totalItems, totalBytes)
        if (retryItems.isEmpty() && state.nextItemIndex > 0) {
            val completed = plan.items.take(state.nextItemIndex)
            context.progress(completed.size, completed.sumOf(PlannedTransferItem::sizeBytes), "Restoring")
        }

        if (state.phase == TransferPhase.COPY) {
            state = copyPhase(plan, state, stateWriter, context)
        }
        if (state.phase == TransferPhase.DELETE_SOURCES) {
            state = deleteSourcesPhase(plan, state, stateWriter, context)
        }
        if (state.phase == TransferPhase.FINALIZE_BACKUPS) {
            state = finalizeBackupsPhase(plan, state, stateWriter, context)
        }

        val finalStatus = if (state.failedItemIndices.isEmpty()) {
            DurableTransferStatus.COMPLETED
        } else {
            DurableTransferStatus.COMPLETED_WITH_ERRORS
        }
        state = state.copy(
            status = finalStatus,
            phase = TransferPhase.COMPLETE,
            retryItemIndices = emptyList(),
            retryPosition = 0,
            lastMessage = if (state.failedItemIndices.isEmpty()) null else "${state.failedItemIndices.size} items failed",
            updatedAtMillis = System.currentTimeMillis(),
        )
        stateWriter.saveState(state)
        if (state.failedItemIndices.isNotEmpty()) {
            context.completeWithErrors(state.failedItemIndices.size, requireNotNull(state.lastMessage))
        } else {
            context.setRetryable(false)
        }
        return state
    }

    fun prepareRetry(state: DurableTransferState): DurableTransferState {
        require(state.status in setOf(
            DurableTransferStatus.FAILED,
            DurableTransferStatus.CANCELLED,
            DurableTransferStatus.INTERRUPTED,
            DurableTransferStatus.COMPLETED_WITH_ERRORS,
        )) { "The operation cannot be retried" }
        return if (state.status == DurableTransferStatus.COMPLETED_WITH_ERRORS && state.failedItemIndices.isNotEmpty()) {
            state.copy(
                status = DurableTransferStatus.QUEUED,
                phase = TransferPhase.COPY,
                retryItemIndices = state.failedItemIndices,
                retryPosition = 0,
                failedItemIndices = emptyList(),
                lastMessage = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        } else {
            state.copy(
                status = DurableTransferStatus.QUEUED,
                lastMessage = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    fun markInterrupted(state: DurableTransferState, message: String): DurableTransferState = state.copy(
        status = DurableTransferStatus.INTERRUPTED,
        lastMessage = message.take(500),
        updatedAtMillis = System.currentTimeMillis(),
    )

    suspend fun restoreBackupsAfterCopyCancellation(
        plan: DurableTransferPlan,
        state: DurableTransferState,
        writer: DurableTransferStateWriter,
    ): DurableTransferState {
        // Persist the rewind before touching backups. A crash half-way through
        // rollback must not resume past files whose old contents were restored.
        val cancelled = state.copy(
            status = DurableTransferStatus.CANCELLED,
            nextItemIndex = if (state.phase == TransferPhase.COPY) 0 else state.nextItemIndex,
            retryPosition = if (state.phase == TransferPhase.COPY) 0 else state.retryPosition,
            lastMessage = "Canceled by the user; completed copies may remain at the destination",
            updatedAtMillis = System.currentTimeMillis(),
        )
        writer.saveState(cancelled)
        if (state.phase != TransferPhase.COPY) return cancelled
        plan.items.asReversed().forEach { item ->
            restoreBackup(plan, item)
            partialFile(plan, item).delete()
        }
        return cancelled
    }

    private suspend fun copyPhase(
        plan: DurableTransferPlan,
        initial: DurableTransferState,
        writer: DurableTransferStateWriter,
        context: OperationContext,
    ): DurableTransferState {
        var state = initial
        val retry = state.retryItemIndices
        val work = if (retry.isEmpty()) {
            (state.nextItemIndex until plan.items.size).toList()
        } else {
            retry.drop(state.retryPosition)
        }

        work.forEachIndexed { workOffset, itemIndex ->
            currentCoroutineContext().ensureActive()
            context.checkpoint()
            val item = plan.items[itemIndex]
            try {
                copyOne(plan, item, context)
                context.logDebug("Copied: ${item.sourcePath} -> ${item.targetPath} (${com.shovel.filemanager.core.FileSystemRules.humanBytes(item.sizeBytes)})")
                state = if (retry.isEmpty()) {
                    state.copy(nextItemIndex = itemIndex + 1, failedItemIndices = state.failedItemIndices - itemIndex, lastMessage = null)
                } else {
                    state.copy(retryPosition = state.retryPosition + 1, failedItemIndices = state.failedItemIndices - itemIndex, lastMessage = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = "${item.sourcePath}: ${error.message ?: error::class.java.simpleName}".take(500)
                if (plan.failurePolicy == TransferFailurePolicy.SKIP_AND_CONTINUE && !plan.move) {
                    restoreBackup(plan, item)
                    val failures = (state.failedItemIndices + itemIndex).distinct().take(MAX_RECORDED_FAILURES)
                    state = if (retry.isEmpty()) {
                        state.copy(nextItemIndex = itemIndex + 1, failedItemIndices = failures, lastMessage = message)
                    } else {
                        state.copy(retryPosition = state.retryPosition + 1, failedItemIndices = failures, lastMessage = message)
                    }
                    context.logWarn(message)
                    context.note("Skipped: ${File(item.sourcePath).name}")
                } else {
                    state = state.copy(
                        status = DurableTransferStatus.FAILED,
                        failedItemIndices = (state.failedItemIndices + itemIndex).distinct().take(MAX_RECORDED_FAILURES),
                        lastMessage = message,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    writer.saveState(state)
                    throw error
                }
            }
            if ((workOffset + 1) % STATE_CHECKPOINT_ITEMS == 0 || workOffset == work.lastIndex) {
                state = state.copy(updatedAtMillis = System.currentTimeMillis())
                writer.saveState(state)
            }
        }

        val nextPhase = if (plan.move && state.failedItemIndices.isEmpty()) TransferPhase.DELETE_SOURCES else TransferPhase.FINALIZE_BACKUPS
        state = state.copy(
            phase = nextPhase,
            retryItemIndices = emptyList(),
            retryPosition = 0,
            updatedAtMillis = System.currentTimeMillis(),
        )
        writer.saveState(state)
        return state
    }

    private suspend fun deleteSourcesPhase(
        plan: DurableTransferPlan,
        initial: DurableTransferState,
        writer: DurableTransferStateWriter,
        context: OperationContext,
    ): DurableTransferState {
        var state = initial
        require(state.failedItemIndices.isEmpty()) { "Sources are not removed while the plan contains errors" }
        // A skipped root has no planned items. Never recursively sweep a root:
        // it can also contain files added after the user confirmed this plan.
        val itemsByRoot = plan.items.groupBy(PlannedTransferItem::sourceRootIndex)
        for (rootIndex in state.nextDeleteRootIndex until plan.sourceRoots.size) {
            currentCoroutineContext().ensureActive()
            context.checkpoint()
            itemsByRoot[rootIndex].orEmpty().asReversed().forEach { item ->
                context.checkpoint()
                validateItemPaths(plan, item)
                val source = File(item.sourcePath)
                require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links are not removed during a move" }
                if (!source.exists()) return@forEach // A preceding attempt already removed this item.
                verifySource(item, source)
                val target = File(item.targetPath)
                require(if (item.directory) target.isDirectory else filesEquivalent(source, target, plan.verification)) {
                    "The destination changed before the source was removed: ${target.name}"
                }
                // File.delete only removes an empty directory. Unplanned children
                // remain at the source and cause an explicit, retryable failure.
                require(source.delete() || !source.exists()) { "Could not remove ${source.name}" }
                context.progress(itemDelta = 1, currentName = source.name)
            }
            state = state.copy(nextDeleteRootIndex = rootIndex + 1, updatedAtMillis = System.currentTimeMillis())
            writer.saveState(state)
        }
        state = state.copy(phase = TransferPhase.FINALIZE_BACKUPS, updatedAtMillis = System.currentTimeMillis())
        writer.saveState(state)
        return state
    }

    private suspend fun finalizeBackupsPhase(
        plan: DurableTransferPlan,
        initial: DurableTransferState,
        writer: DurableTransferStateWriter,
        context: OperationContext,
    ): DurableTransferState {
        var state = initial
        for (index in state.nextFinalizeItemIndex until plan.items.size) {
            currentCoroutineContext().ensureActive()
            context.checkpoint()
            val backup = backupFile(plan, plan.items[index])
            if (backup.exists()) deleteTree(backup)
            partialFile(plan, plan.items[index]).delete()
            state = state.copy(nextFinalizeItemIndex = index + 1)
            if ((index + 1) % STATE_CHECKPOINT_ITEMS == 0 || index == plan.items.lastIndex) {
                state = state.copy(updatedAtMillis = System.currentTimeMillis())
                writer.saveState(state)
            }
        }
        return state
    }

    private suspend fun copyOne(plan: DurableTransferPlan, item: PlannedTransferItem, context: OperationContext) {
        validateItemPaths(plan, item)
        val source = File(item.sourcePath)
        verifySource(item, source)
        val target = File(item.targetPath)
        if (item.directory) {
            ensureDirectory(plan, item, target)
            context.progress(itemDelta = 1, currentName = source.name)
            return
        }

        // Equal length is not proof that an existing file is our completed copy.
        // Only byte-identical content may be reused after an interrupted operation.
        // The selected SIZE/SHA256 policy still applies to newly written copies.
        if (target.isFile && filesEquivalent(source, target, TransferVerification.SHA256)) {
            context.progress(itemDelta = 1, byteDelta = item.sizeBytes, currentName = source.name)
            return
        }
        prepareTargetForReplacement(plan, item, target)
        val partial = partialFile(plan, item)
        if (partial.exists() && !partial.delete()) throw IllegalStateException("Could not remove the interrupted partial copy")
        partial.parentFile?.let { parent -> require(parent.isDirectory || parent.mkdirs()) { "The destination folder is unavailable" } }

        val sourceDigest = if (plan.verification == TransferVerification.SHA256) MessageDigest.getInstance("SHA-256") else null
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        context.checkpoint()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sourceDigest?.update(buffer, 0, read)
                        context.progress(byteDelta = read.toLong(), currentName = source.name)
                    }
                    output.fd.sync()
                    buffer.fill(0)
                }
            }
            verifySource(item, source)
            require(partial.length() == item.sizeBytes) { "The copy size does not match" }
            if (sourceDigest != null) {
                require(MessageDigest.isEqual(sourceDigest.digest(), sha256(partial))) { "SHA-256 verification failed" }
            }
            validateItemPaths(plan, item)
            // The existing target was already put aside, if replacement was
            // authorized. A target appearing during the copy belongs to someone
            // else. Do not use REPLACE_EXISTING or ATOMIC_MOVE here (the latter
            // may replace an existing target even without REPLACE_EXISTING).
            require(!target.exists()) { "The destination changed after the plan was confirmed: ${target.name}" }
            Files.move(partial.toPath(), target.toPath())
            target.setLastModified(item.modifiedAtMillis)
            require(filesEquivalent(source, target, plan.verification)) { "Final copy verification failed" }
            context.progress(itemDelta = 1, currentName = source.name)
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    private fun verifySource(item: PlannedTransferItem, source: File) {
        require(source.exists()) { "The source no longer exists" }
        require(source.isDirectory == item.directory) { "The source type changed" }
        require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links are not supported" }
        if (!item.directory) {
            require(source.length() == item.sizeBytes && source.lastModified() == item.modifiedAtMillis) {
                "The source changed after the plan was confirmed"
            }
        }
    }

    private fun validateItemPaths(plan: DurableTransferPlan, item: PlannedTransferItem) {
        val source = File(item.sourcePath)
        val target = File(item.targetPath)
        require(source.canonicalPath == item.sourcePath) { "The source changed after the plan was confirmed" }
        require(target.canonicalPath == item.targetPath &&
            File(plan.destinationPath).canonicalPath == plan.destinationPath &&
            FileSystemRules.isContained(File(plan.destinationPath), target)) { "The target escapes the destination folder" }
        require(source.canonicalPath != target.canonicalPath) { "Source and destination are the same file" }
    }

    private fun ensureDirectory(plan: DurableTransferPlan, item: PlannedTransferItem, target: File) {
        if (target.isDirectory) return
        if (target.exists()) prepareTargetForReplacement(plan, item, target)
        require(!target.exists() && (target.mkdir() || target.isDirectory)) { "Could not create ${target.name}" }
    }

    private fun prepareTargetForReplacement(plan: DurableTransferPlan, item: PlannedTransferItem, target: File) {
        if (!target.exists()) return
        require(item.replaceExisting) { "The destination changed after the plan was confirmed: ${target.name}" }
        val backup = backupFile(plan, item)
        require(!backup.exists() && !Files.isSymbolicLink(backup.toPath())) { "Could not safely move the existing file aside" }
        Files.move(target.toPath(), backup.toPath())
    }

    private suspend fun restoreBackup(plan: DurableTransferPlan, item: PlannedTransferItem) {
        validateItemPaths(plan, item)
        val backup = backupFile(plan, item)
        require(!Files.isSymbolicLink(backup.toPath())) { "Symbolic links cannot be overwritten" }
        if (!backup.exists()) return
        val target = File(item.targetPath)
        if (target.exists()) {
            // Never erase unrelated files or children created after this copy.
            require(if (item.directory) target.isDirectory else
                filesEquivalent(File(item.sourcePath), target, TransferVerification.SHA256)) {
                "The destination changed after the plan was confirmed: ${target.name}"
            }
            require(target.delete()) { "Could not restore the previous destination" }
        }
        Files.move(backup.toPath(), target.toPath())
    }

    private suspend fun filesEquivalent(source: File, target: File, verification: TransferVerification): Boolean {
        if (!source.isFile || !target.isFile || source.length() != target.length()) return false
        return verification == TransferVerification.SIZE || MessageDigest.isEqual(sha256(source), sha256(target))
    }

    private suspend fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            buffer.fill(0)
        }
        return digest.digest()
    }

    private fun backupFile(plan: DurableTransferPlan, item: PlannedTransferItem): File =
        File(File(item.targetPath).parentFile, ".shovel-backup-${plan.id}-${item.index}")

    private fun partialFile(plan: DurableTransferPlan, item: PlannedTransferItem): File =
        File(File(item.targetPath).parentFile, ".shovel-part-${plan.id}-${item.index}.tmp")

    private fun deleteTree(file: File) {
        if (Files.isSymbolicLink(file.toPath())) {
            require(file.delete()) { "Could not remove ${file.name}" }
            return
        }
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        require(file.delete() || !file.exists()) { "Could not remove ${file.name}" }
    }

    private fun validatePlanState(plan: DurableTransferPlan, state: DurableTransferState) {
        require(plan.schemaVersion == 1 && state.schemaVersion == 1) { "Unsupported operation version" }
        require(plan.id == state.planId) { "The plan and state identities do not match" }
        require(plan.items.size <= DurableTransferPlanner.MAX_PLAN_ITEMS) { "The plan item limit was exceeded" }
        require(state.nextItemIndex in 0..plan.items.size) { "Invalid operation checkpoint" }
        require(state.failedItemIndices.size <= MAX_RECORDED_FAILURES) { "The error-list limit was exceeded" }
        require(state.retryItemIndices.size <= MAX_RECORDED_FAILURES) { "The retry-list limit was exceeded" }
        require(state.failedItemIndices.all { it in plan.items.indices }) { "Invalid failed-item index" }
        require(state.retryItemIndices.all { it in plan.items.indices }) { "Invalid retry-item index" }
    }
}
