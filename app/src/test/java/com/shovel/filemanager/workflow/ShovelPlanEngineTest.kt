package com.shovel.filemanager.workflow

import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.operations.OperationContext
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShovelPlanEngineTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun oneSourceCopiesToTwoIndependentDestinationsWithStrongReceipts() = runTest {
        val storage = FakeShovelStorage()
        val sourceLocation = storage.file("/phone/report.txt", "content", "phone")
        val first = storage.directory("/first", "server-a")
        val second = storage.directory("/second", "server-b")
        val plan = plan(
            listOf(ShovelSourceRef(sourceLocation, "report.txt")),
            listOf(ShovelDestinationRef(first), ShovelDestinationRef(second)),
            verification = TransferVerification.SHA256,
        )
        val result = execute(plan, storage)

        assertEquals(ShovelExecutionStatus.COMPLETED, result.first.status)
        assertArrayEquals("content".toByteArray(), storage.bytes(child(first, "report.txt")))
        assertArrayEquals("content".toByteArray(), storage.bytes(child(second, "report.txt")))
        assertEquals(2, result.second.items.count { it.status == ShovelReceiptItemStatus.COPIED })
        assertTrue(result.second.items.all { it.directory || it.sha256?.length == 64 })
    }

    @Test
    fun moveDeletesSourceOnlyAfterEveryRequiredDestinationPassesSha256() = runTest {
        val storage = FakeShovelStorage()
        val sourceLocation = storage.file("/phone/photo.bin", byteArrayOf(1, 2, 3), "phone")
        val first = storage.directory("/one", "server-a")
        val second = storage.directory("/two", "server-b")
        val corruptTarget = child(second, "photo.bin")
        storage.corruptInstallAt = corruptTarget.identityKey()
        val plan = plan(
            listOf(ShovelSourceRef(sourceLocation, "photo.bin")),
            listOf(ShovelDestinationRef(first), ShovelDestinationRef(second)),
            verification = TransferVerification.SIZE,
            deleteSources = true,
        )
        val preflight = ShovelPlanPreflight().preview(plan, storage)
        val writer = RecordingWriter()

        val failure = runCatching {
            ShovelPlanEngine(temporary.newFolder()).execute(
                preflight,
                ShovelExecutionState(executionId = preflight.executionId),
                emptyList(),
                storage,
                writer,
                OperationContext.background(),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(storage.exists(sourceLocation))
        assertFalse(writer.states.last().sourcesDeleted)
        assertEquals(ShovelExecutionStatus.FAILED, writer.states.last().status)
    }

    @Test
    fun moveDeletesSourceAfterAllRequiredCopiesAreStronglyRechecked() = runTest {
        val storage = FakeShovelStorage()
        val sourceLocation = storage.file("/phone/photo.bin", byteArrayOf(1, 2, 3), "phone")
        val first = storage.directory("/one", "server-a")
        val second = storage.directory("/two", "server-b")
        val result = execute(
            plan(
                listOf(ShovelSourceRef(sourceLocation, "photo.bin")),
                listOf(ShovelDestinationRef(first), ShovelDestinationRef(second)),
                verification = TransferVerification.SIZE,
                deleteSources = true,
            ),
            storage,
        )

        assertEquals(ShovelExecutionStatus.COMPLETED, result.first.status)
        assertTrue(result.first.sourcesDeleted)
        assertFalse(storage.exists(sourceLocation))
        assertFalse(result.second.undoAvailable)
    }

    @Test
    fun failedReplacementRestoresThePreviousFileAndClearsRecoveryCheckpoint() = runTest {
        val storage = FakeShovelStorage()
        val sourceLocation = storage.file("/phone/report.txt", "new", "phone")
        val destination = storage.directory("/target", "server")
        val target = storage.file("/target/report.txt", "old", "server")
        storage.failInstallAt = target.identityKey()
        val plan = plan(
            listOf(ShovelSourceRef(sourceLocation, "report.txt")),
            listOf(ShovelDestinationRef(destination)),
            conflict = ConflictPolicy.REPLACE,
            verification = TransferVerification.SHA256,
        )
        val preflight = ShovelPlanPreflight().preview(plan, storage)
        val writer = RecordingWriter()

        val failure = runCatching {
            ShovelPlanEngine(temporary.newFolder()).execute(
                preflight,
                ShovelExecutionState(executionId = preflight.executionId),
                emptyList(),
                storage,
                writer,
                OperationContext.background(),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertArrayEquals("old".toByteArray(), storage.bytes(target))
        assertEquals(null, writer.states.last().activeBackup)
        assertEquals(ShovelExecutionStatus.FAILED, writer.states.last().status)
    }

    @Test
    fun undoRefusesAChangedCopyAndRemovesAnUnchangedCopy() = runTest {
        val storage = FakeShovelStorage()
        val sourceLocation = storage.file("/phone/note.txt", "original", "phone")
        val destination = storage.directory("/target", "server")
        val (_, receipt) = execute(
            plan(
                listOf(ShovelSourceRef(sourceLocation, "note.txt")),
                listOf(ShovelDestinationRef(destination)),
                verification = TransferVerification.SHA256,
            ),
            storage,
        )
        val target = child(destination, "note.txt")
        val undo = ShovelTimelineUndoEngine()
        val safePreview = undo.preview(receipt, storage)
        assertTrue(safePreview.canRun)

        storage.file("/target/note.txt", "changed", "server")
        val changedPreview = undo.preview(receipt, storage)
        assertFalse(changedPreview.canRun)
        assertEquals(ShovelUndoDisposition.CHANGED, changedPreview.items.single().disposition)

        storage.file("/target/note.txt", "original", "server")
        undo.execute(receipt, undo.preview(receipt, storage), storage, OperationContext.background())
        assertFalse(storage.exists(target))
        assertTrue(storage.exists(sourceLocation))
    }

    @Test
    fun undoRefusesToRemoveACopiedFolderAfterAnUnrecordedChildAppears() = runTest {
        val storage = FakeShovelStorage()
        val sourceRoot = storage.directory("/phone/project", "phone")
        storage.file("/phone/project/copied.txt", "copy", "phone")
        val destination = storage.directory("/target", "server")
        val (_, receipt) = execute(
            plan(
                listOf(ShovelSourceRef(sourceRoot, "project")),
                listOf(ShovelDestinationRef(destination)),
                verification = TransferVerification.SHA256,
            ),
            storage,
        )
        val targetRoot = child(destination, "project")
        val unexpected = storage.file("/target/project/created-later.txt", "keep", "server")

        val preview = ShovelTimelineUndoEngine().preview(receipt, storage)

        assertFalse(preview.canRun)
        assertTrue(preview.items.any {
            it.location.identityKey() == targetRoot.identityKey() &&
                it.disposition == ShovelUndoDisposition.CHANGED &&
                it.detail == "The folder contains items that were not created by this operation"
        })
        assertTrue(storage.exists(child(targetRoot, "copied.txt")))
        assertTrue(storage.exists(unexpected))
    }

    @Test
    fun interruptedExecutionContinuesFromItsDurableCheckpointWithoutCopyingCompletedFilesAgain() = runTest {
        val storage = FakeShovelStorage()
        val sourceRoot = storage.directory("/phone/project", "phone")
        val firstSource = storage.file("/phone/project/first.txt", "first", "phone")
        storage.file("/phone/project/second.txt", "second", "phone")
        val destination = storage.directory("/server", "server")
        val plan = plan(
            sources = listOf(ShovelSourceRef(sourceRoot, "project")),
            destinations = listOf(ShovelDestinationRef(destination)),
            verification = TransferVerification.SHA256,
        )
        val preview = ShovelPlanPreflight().preview(plan, storage)
        val targetRoot = child(destination, "project")
        val firstTarget = child(targetRoot, "first.txt")
        storage.createDirectory(targetRoot)
        storage.file(firstTarget.path, "first", "server")
        val firstSha = storage.sha256(firstSource)
        val initialJournal = listOf(
            ShovelReceiptItem(
                source = sourceRoot,
                destination = targetRoot,
                directory = true,
                status = ShovelReceiptItemStatus.COPIED,
                sizeBytes = 0,
            ),
            ShovelReceiptItem(
                source = firstSource,
                destination = firstTarget,
                directory = false,
                status = ShovelReceiptItemStatus.COPIED,
                sizeBytes = 5,
                sha256 = firstSha,
            ),
        )
        val state = ShovelExecutionState(
            executionId = preview.executionId,
            status = ShovelExecutionStatus.INTERRUPTED,
            nextProjectionIndex = 0,
            nextEntryIndex = 2,
            completedFiles = 1,
            completedBytes = 5,
            attempt = 1,
            startedAtMillis = 10,
        )

        val result = ShovelPlanEngine(temporary.newFolder()).execute(
            preview,
            state,
            initialJournal,
            storage,
            RecordingWriter(),
            OperationContext.background(),
        )

        assertEquals(ShovelExecutionStatus.COMPLETED, result.first.status)
        assertEquals(2, result.first.attempt)
        assertEquals(2, result.first.completedFiles)
        assertEquals(listOf(child(targetRoot, "second.txt").identityKey()), storage.installed)
        assertArrayEquals("first".toByteArray(), storage.bytes(firstTarget))
        assertArrayEquals("second".toByteArray(), storage.bytes(child(targetRoot, "second.txt")))
        assertEquals(3, result.second.items.size)
    }

    @Test
    fun replacementUndoRestoresThePreviousVersionOnlyWhileTheNewVersionStillMatches() = runTest {
        val storage = FakeShovelStorage()
        val sourceLocation = storage.file("/phone/report.txt", "new", "phone")
        val destination = storage.directory("/target", "server")
        val target = storage.file("/target/report.txt", "old", "server")
        val (_, receipt) = execute(
            plan(
                listOf(ShovelSourceRef(sourceLocation, "report.txt")),
                listOf(ShovelDestinationRef(destination)),
                conflict = ConflictPolicy.REPLACE,
                verification = TransferVerification.SHA256,
            ),
            storage,
        )
        val replacement = receipt.items.single()
        assertEquals(ShovelReceiptItemStatus.REPLACED, replacement.status)
        assertNotNull(replacement.backup)

        val undo = ShovelTimelineUndoEngine()
        val preview = undo.preview(receipt, storage)
        assertTrue(preview.canRun)
        undo.execute(receipt, preview, storage, OperationContext.background())

        assertArrayEquals("old".toByteArray(), storage.bytes(target))
        assertFalse(storage.exists(requireNotNull(replacement.backup)))
    }

    @Test
    fun interruptedMoveResumesItsItemizedSourceDeletionWithoutNeedingDeletedSources() = runTest {
        val storage = FakeShovelStorage()
        val sourceRoot = storage.directory("/phone/folder", "phone")
        val first = storage.file("/phone/folder/a.txt", "a", "phone")
        val second = storage.file("/phone/folder/b.txt", "b", "phone")
        val destination = storage.directory("/server", "server")
        val plan = plan(
            sources = listOf(ShovelSourceRef(sourceRoot, "folder")),
            destinations = listOf(ShovelDestinationRef(destination)),
            verification = TransferVerification.SIZE,
            deleteSources = true,
        )
        val preview = ShovelPlanPreflight().preview(plan, storage)
        val writer = RecordingWriter()
        storage.failDeleteAt = first.identityKey()

        val failure = runCatching {
            ShovelPlanEngine(temporary.newFolder()).execute(
                preview,
                ShovelExecutionState(executionId = preview.executionId),
                emptyList(),
                storage,
                writer,
                OperationContext.background(),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(writer.states.last().sourceDeletionPrepared)
        assertTrue(storage.exists(sourceRoot))
        assertTrue(storage.exists(first))
        assertFalse(storage.exists(second))
        assertTrue(writer.journals.last().any { it.status == ShovelReceiptItemStatus.DELETED && it.source == second })

        storage.failDeleteAt = null
        val resumeState = writer.states.last().copy(
            status = ShovelExecutionStatus.INTERRUPTED,
            failedItems = 0,
            errors = emptyList(),
            finishedAtMillis = null,
        )
        val resumed = ShovelPlanEngine(temporary.newFolder()).execute(
            preview,
            resumeState,
            writer.journals.last(),
            storage,
            RecordingWriter(),
            OperationContext.background(),
        )

        assertEquals(ShovelExecutionStatus.COMPLETED, resumed.first.status)
        assertTrue(resumed.first.sourcesDeleted)
        assertFalse(storage.exists(sourceRoot))
        assertEquals(3, resumed.second.items.count { it.status == ShovelReceiptItemStatus.DELETED })
        assertTrue(resumed.second.items.filter { it.status == ShovelReceiptItemStatus.DELETED && !it.directory }.all {
            it.sha256?.length == 64
        })
    }

    @Test
    fun retryAfterContinueOnErrorVerifiesCompletedFilesAndCopiesOnlyTheFailedFile() = runTest {
        val storage = FakeShovelStorage()
        val sourceRoot = storage.directory("/phone/project", "phone")
        storage.file("/phone/project/first.txt", "first", "phone")
        storage.file("/phone/project/second.txt", "second", "phone")
        val destination = storage.directory("/server", "server")
        val targetRoot = child(destination, "project")
        val failedTarget = child(targetRoot, "second.txt")
        storage.failInstallAt = failedTarget.identityKey()
        val plan = plan(
            sources = listOf(ShovelSourceRef(sourceRoot, "project")),
            destinations = listOf(ShovelDestinationRef(destination)),
            verification = TransferVerification.SHA256,
            failure = TransferFailurePolicy.SKIP_AND_CONTINUE,
        )
        val preview = ShovelPlanPreflight().preview(plan, storage)
        val writer = RecordingWriter()
        val first = ShovelPlanEngine(temporary.newFolder()).execute(
            preview,
            ShovelExecutionState(executionId = preview.executionId),
            emptyList(),
            storage,
            writer,
            OperationContext.background(),
        )

        assertEquals(ShovelExecutionStatus.COMPLETED_WITH_ERRORS, first.first.status)
        assertEquals(1, first.first.failedItems)
        val firstTarget = child(targetRoot, "first.txt")
        assertArrayEquals("first".toByteArray(), storage.bytes(firstTarget))
        val installsBeforeRetry = storage.installed.toList()

        storage.failInstallAt = null
        val prepared = ShovelExecutionRetry.prepare(ShovelExecutionRecord(preview, first.first, first.second.items))
        val retried = ShovelPlanEngine(temporary.newFolder()).execute(
            prepared.preflight,
            prepared.state,
            prepared.journal,
            storage,
            RecordingWriter(),
            OperationContext.background(),
        )

        assertEquals(ShovelExecutionStatus.COMPLETED, retried.first.status)
        assertEquals(0, retried.first.failedItems)
        assertArrayEquals("first".toByteArray(), storage.bytes(firstTarget))
        assertArrayEquals("second".toByteArray(), storage.bytes(failedTarget))
        assertEquals(installsBeforeRetry + failedTarget.identityKey(), storage.installed)
        assertFalse(retried.second.items.any { it.status == ShovelReceiptItemStatus.FAILED })
    }

    private suspend fun execute(
        plan: ShovelPlanDefinition,
        storage: FakeShovelStorage,
    ): Pair<ShovelExecutionState, ShovelOperationReceipt> {
        val preview = ShovelPlanPreflight().preview(plan, storage)
        assertTrue(preview.blockers.toString(), preview.canRun)
        return ShovelPlanEngine(temporary.newFolder()).execute(
            preview,
            ShovelExecutionState(executionId = preview.executionId),
            emptyList(),
            storage,
            RecordingWriter(),
            OperationContext.background(),
        )
    }

    private fun plan(
        sources: List<ShovelSourceRef>,
        destinations: List<ShovelDestinationRef>,
        conflict: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        verification: TransferVerification = TransferVerification.SIZE,
        failure: TransferFailurePolicy = TransferFailurePolicy.STOP,
        deleteSources: Boolean = false,
    ) = ShovelPlanDefinition(
        id = "engine-plan",
        name = "Engine test",
        sources = sources,
        destinations = destinations,
        conflictPolicy = conflict,
        verification = verification,
        failurePolicy = failure,
        deleteSourcesAfterVerifiedCopies = deleteSources,
    )

    private class RecordingWriter : ShovelExecutionWriter {
        val states = mutableListOf<ShovelExecutionState>()
        val journals = mutableListOf<List<ShovelReceiptItem>>()
        override fun saveState(state: ShovelExecutionState) {
            states += state
        }

        override fun saveJournal(executionId: String, items: List<ShovelReceiptItem>) {
            journals += items.toList()
        }
    }
}
