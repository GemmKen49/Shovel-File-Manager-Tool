package com.shovel.filemanager.workflow

import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShovelWorkflowRepositoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun plansRoundTripWithoutEmbeddingRemoteCredentials() {
        val root = temporary.newFolder("plans")
        val repository = ShovelPlanRepository(root)
        val plan = ShovelPlanDefinition(
            id = "portable-plan",
            name = "Phone to server",
            sources = listOf(
                ShovelSourceRef(ShovelLocationRef.remote("source-profile", "Source", "/in/report.txt"), "report.txt"),
            ),
            destinations = listOf(
                ShovelDestinationRef(ShovelLocationRef.remote("target-profile", "Target", "/archive")),
            ),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            verification = TransferVerification.SHA256,
            failurePolicy = TransferFailurePolicy.STOP,
        )

        val saved = repository.save(plan)
        val restored = repository.find(plan.id)
        val persisted = File(root, "shovel_workflows_v1/plans.json").readText()

        assertEquals(saved, restored)
        assertTrue(persisted.contains("source-profile"))
        assertTrue(persisted.contains("target-profile"))
        assertFalse(persisted.contains("password", ignoreCase = true))
        assertFalse(persisted.contains("credential", ignoreCase = true))
    }

    @Test
    fun malformedMetadataIsQuarantinedInsteadOfPartiallyLoaded() {
        val root = temporary.newFolder("corrupt")
        val repository = ShovelPlanRepository(root)
        val storageDirectory = File(root, "shovel_workflows_v1")
        File(storageDirectory, "plans.json").writeText("{not-json")

        assertTrue(repository.list().isEmpty())
        assertFalse(File(storageDirectory, "plans.json").exists())
        assertEquals(1, File(storageDirectory, "corrupt").listFiles()?.size)
    }

    @Test
    fun interruptedExecutionPersistsItsRecoveryBackupAndJournal() = runTest {
        val root = temporary.newFolder("execution")
        val storage = FakeShovelStorage()
        val source = storage.file("/phone/file.txt", "content", "phone")
        val destination = storage.directory("/server", "server")
        val plan = ShovelPlanDefinition(
            id = "recovery-plan",
            name = "Recovery",
            sources = listOf(ShovelSourceRef(source, "file.txt")),
            destinations = listOf(ShovelDestinationRef(destination)),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            verification = TransferVerification.SHA256,
            failurePolicy = TransferFailurePolicy.STOP,
        )
        val preview = ShovelPlanPreflight().preview(plan, storage, nowMillis = 50)
        val repository = ShovelExecutionRepository(root)
        repository.create(preview)
        val backup = ShovelLocationRef.remote("server", "server", "/.file.shovel-backup")
        val state = ShovelExecutionState(
            executionId = preview.executionId,
            status = ShovelExecutionStatus.INTERRUPTED,
            nextEntryIndex = 1,
            activeBackup = backup,
            attempt = 1,
            startedAtMillis = 60,
        )
        val journal = listOf(
            ShovelReceiptItem(
                source = source,
                destination = child(destination, "file.txt"),
                directory = false,
                status = ShovelReceiptItemStatus.COPIED,
                sizeBytes = 7,
                sha256 = storage.sha256(source),
            ),
        )
        repository.saveJournal(preview.executionId, journal)
        repository.save(state)

        val restored = repository.load(preview.executionId)

        assertEquals(preview, restored.preflight)
        assertEquals(state, restored.state)
        assertEquals(journal, restored.journal)
        assertEquals(backup, restored.state.activeBackup)
        assertNotNull(repository.listRecoverable().singleOrNull())
    }

    @Test
    fun consumedUndoIsDurablyRemovedFromTheOriginalReceipt() {
        val root = temporary.newFolder("timeline")
        val repository = ShovelTimelineRepository(root)
        val source = ShovelLocationRef.local(File(root, "source.txt").path)
        val destination = ShovelLocationRef.local(File(root, "destination.txt").path)
        val receipt = ShovelOperationReceipt(
            id = "receipt-1",
            planId = "plan-1",
            planName = "Copy",
            startedAtMillis = 10,
            finishedAtMillis = 20,
            status = ShovelExecutionStatus.COMPLETED,
            verification = TransferVerification.SHA256,
            items = listOf(
                ShovelReceiptItem(
                    source = source,
                    destination = destination,
                    directory = false,
                    status = ShovelReceiptItemStatus.COPIED,
                    sizeBytes = 1,
                    sha256 = "a".repeat(64),
                ),
            ),
            sourceDeletionRequested = false,
            sourcesDeleted = false,
            undoAvailable = true,
            errorCount = 0,
        )
        repository.add(receipt)

        assertTrue(repository.markUndoConsumed(receipt.id))
        assertFalse(requireNotNull(repository.get(receipt.id)).undoAvailable)
    }

    @Test
    fun timelineSearchFollowsTheFileAcrossSeveralShovelOperations() {
        val root = temporary.newFolder("trace")
        val first = receipt(
            id = "receipt-first",
            source = ShovelLocationRef.local(File(root, "original/report.txt").path),
            destination = ShovelLocationRef.remote("server", "Server", "/incoming/report.txt"),
            finished = 20,
        )
        val second = receipt(
            id = "receipt-second",
            source = ShovelLocationRef.remote("server", "Server", "/incoming/report.txt"),
            destination = ShovelLocationRef.remote("server", "Server", "/archive/report.txt"),
            finished = 30,
        )
        val unrelated = receipt(
            id = "receipt-other",
            source = ShovelLocationRef.local(File(root, "photo.jpg").path),
            destination = ShovelLocationRef.remote("server", "Server", "/photos/photo.jpg"),
            finished = 40,
        )

        val traced = ShovelTimelineSearch.trace(listOf(unrelated, second, first), "original/report.txt")

        assertEquals(listOf("receipt-second", "receipt-first"), traced.map(ShovelOperationReceipt::id))
    }

    private fun receipt(
        id: String,
        source: ShovelLocationRef,
        destination: ShovelLocationRef,
        finished: Long,
    ) = ShovelOperationReceipt(
        id = id,
        planId = "plan-trace",
        planName = "Trace",
        startedAtMillis = finished - 1,
        finishedAtMillis = finished,
        status = ShovelExecutionStatus.COMPLETED,
        verification = TransferVerification.SHA256,
        items = listOf(
            ShovelReceiptItem(
                source = source,
                destination = destination,
                directory = false,
                status = ShovelReceiptItemStatus.COPIED,
                sizeBytes = 1,
                sha256 = "b".repeat(64),
            ),
        ),
        sourceDeletionRequested = false,
        sourcesDeleted = false,
        undoAvailable = true,
        errorCount = 0,
    )
}
