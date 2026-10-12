package com.shovel.filemanager.workflow

import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShovelAutomationPolicyTest {
    @Test
    fun unchangedStrongPreviewIsAllowedButAChangedSourceNeedsNewApproval() = runTest {
        val storage = FakeShovelStorage()
        val source = storage.file("/phone/report.txt", "first", "phone")
        val destination = storage.directory("/server", "server")
        val plan = ShovelPlanDefinition(
            id = "scheduled-plan",
            name = "Scheduled",
            sources = listOf(ShovelSourceRef(source, "report.txt")),
            destinations = listOf(ShovelDestinationRef(destination)),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            verification = TransferVerification.SHA256,
            failurePolicy = TransferFailurePolicy.STOP,
        )
        val approved = ShovelPlanPreflight().preview(plan, storage, nowMillis = 10)
        val rule = ShovelAutomationRule(
            id = "rule-1",
            name = "Rule",
            planId = plan.id,
            enabled = true,
            schedule = ShovelAutomationSchedule.DAILY,
            lastPreviewFingerprint = ShovelPreflightFingerprint.create(approved),
        )

        assertNull(ShovelAutomationPolicy.runBlocker(rule, approved))

        storage.file("/phone/report.txt", "other", "phone")
        val changed = ShovelPlanPreflight().preview(plan, storage, nowMillis = 20)
        assertEquals(
            "Files changed; open Shovel Plans and approve the new preview",
            ShovelAutomationPolicy.runBlocker(rule, changed),
        )
    }

    @Test
    fun backgroundItemAndByteBudgetsAreHardStops() = runTest {
        val storage = FakeShovelStorage()
        val source = storage.file("/phone/report.txt", "first", "phone")
        val destination = storage.directory("/server", "server")
        val plan = ShovelPlanDefinition(
            id = "bounded-plan",
            name = "Bounded",
            sources = listOf(ShovelSourceRef(source, "report.txt")),
            destinations = listOf(ShovelDestinationRef(destination)),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            verification = TransferVerification.SHA256,
            failurePolicy = TransferFailurePolicy.STOP,
        )
        val base = ShovelPlanPreflight().preview(plan, storage, nowMillis = 10)
        val entry = base.entries.single()

        val tooMany = base.copy(
            entries = List((ShovelWorkflowLimits.MAX_AUTOMATION_ITEMS + 1).toInt()) { index -> entry.copy(index = index) },
        )
        assertEquals("Preview is too large for background execution", ShovelAutomationPolicy.blocker(tooMany))

        val tooLarge = base.copy(projectedWriteBytes = ShovelWorkflowLimits.MAX_AUTOMATION_BYTES + 1)
        assertEquals("Preview exceeds the background transfer limit", ShovelAutomationPolicy.blocker(tooLarge))
    }

    @Test
    fun itemBudgetCountsEachProjectionOwnSourceEntriesOnly() = runTest {
        val storage = FakeShovelStorage()
        val sources = (0 until 101).map { index ->
            val source = storage.file("/phone/file-$index.txt", "value-$index", "phone")
            ShovelSourceRef(source, "file-$index.txt")
        }
        val destination = storage.directory("/server", "server")
        val plan = ShovelPlanDefinition(
            id = "many-small-sources",
            name = "Many small sources",
            sources = sources,
            destinations = listOf(ShovelDestinationRef(destination)),
            conflictPolicy = ConflictPolicy.KEEP_BOTH,
            verification = TransferVerification.SHA256,
            failurePolicy = TransferFailurePolicy.STOP,
        )
        val preview = ShovelPlanPreflight().preview(plan, storage, nowMillis = 10)

        assertEquals(101, preview.entries.size)
        assertEquals(101, preview.projections.size)
        assertNull(ShovelAutomationPolicy.blocker(preview))
    }

    @Test
    fun workSpecificationPreservesScheduleNetworkAndChargingConstraints() {
        val disabled = ShovelAutomationRule(
            id = "disabled-rule",
            name = "Disabled",
            planId = "plan-1",
        )
        assertNull(ShovelAutomationPolicy.workSpec(disabled))

        val weekly = disabled.copy(
            id = "weekly-rule",
            enabled = true,
            schedule = ShovelAutomationSchedule.WEEKLY,
            unmeteredOnly = true,
            chargingOnly = true,
        ).normalized()
        val spec = requireNotNull(ShovelAutomationPolicy.workSpec(weekly))

        assertEquals(168L, spec.intervalHours)
        assertTrue(spec.unmeteredOnly)
        assertTrue(spec.chargingOnly)
    }
}
