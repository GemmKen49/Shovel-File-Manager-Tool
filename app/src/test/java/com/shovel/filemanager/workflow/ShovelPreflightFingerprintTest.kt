package com.shovel.filemanager.workflow

import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShovelPreflightFingerprintTest {
    @Test
    fun fingerprintIsStableForTheSamePreviewButChangesWithSourceContent() = runTest {
        val storage = FakeShovelStorage()
        val source = storage.file("/phone/rule.txt", "first", "phone")
        val destination = storage.directory("/server", "server")
        val plan = ShovelPlanDefinition(
            id = "automation-plan",
            name = "Automation",
            sources = listOf(ShovelSourceRef(source, "rule.txt")),
            destinations = listOf(ShovelDestinationRef(destination)),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            verification = TransferVerification.SHA256,
            failurePolicy = TransferFailurePolicy.STOP,
        )
        val firstPreview = ShovelPlanPreflight().preview(plan, storage, nowMillis = 100)
        val firstFingerprint = ShovelPreflightFingerprint.create(firstPreview)

        assertEquals(firstFingerprint, ShovelPreflightFingerprint.create(firstPreview))

        storage.file("/phone/rule.txt", "other", "phone")
        val changedPreview = ShovelPlanPreflight().preview(plan, storage, nowMillis = 200)

        assertNotEquals(firstFingerprint, ShovelPreflightFingerprint.create(changedPreview))
    }
}
