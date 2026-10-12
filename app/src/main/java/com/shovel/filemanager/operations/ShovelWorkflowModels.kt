package com.shovel.filemanager.workflow

import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.network.RemotePath
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import java.io.File
import java.util.UUID

object ShovelWorkflowLimits {
    const val MAX_SAVED_PLANS = 64
    const val MAX_DESTINATIONS = 16
    const val MAX_SOURCE_ROOTS = 10_000
    const val MAX_PLANNED_ENTRIES = 200_000
    const val MAX_RECEIPT_ITEMS = 20_000
    const val MAX_TREE_DEPTH = 128
    const val MAX_RECORDED_ERRORS = 100
    const val MAX_TIMELINE_ENTRIES = 256
    const val MAX_AUTOMATION_RULES = 64
    const val MAX_AUTOMATION_ITEMS = 10_000L
    const val MAX_AUTOMATION_BYTES = 1L * 1_024 * 1_024 * 1_024
    const val MAX_PATH_LENGTH = 4_096
    const val MAX_NAME_LENGTH = 120
    const val MAX_RECEIPT_BYTES = 16L * 1_024 * 1_024
    const val MAX_METADATA_BYTES = 32L * 1_024 * 1_024
    const val MAX_TEXT_MERGE_CHARS = 4 * 1_024 * 1_024
    const val MAX_TEXT_MERGE_LINES = 100_000
    const val MIN_STAGING_RESERVE_BYTES = 32L * 1_024 * 1_024
}

enum class ShovelLocationKind { LOCAL, REMOTE }

/** A location reference deliberately contains no credentials. */
data class ShovelLocationRef(
    val kind: ShovelLocationKind,
    val path: String,
    val profileId: String? = null,
    val profileName: String? = null,
) {
    val displayLabel: String
        get() = when (kind) {
            ShovelLocationKind.LOCAL -> path
            ShovelLocationKind.REMOTE -> "${profileName.orEmpty().ifBlank { profileId.orEmpty() }} · $path"
        }

    fun normalized(): ShovelLocationRef = when (kind) {
        ShovelLocationKind.LOCAL -> copy(
            path = File(path).canonicalPath,
            profileId = null,
            profileName = null,
        )
        ShovelLocationKind.REMOTE -> copy(
            path = RemotePath.normalize(path),
            profileId = requireNotNull(profileId).trim().also {
                require(it.matches(SAFE_ID)) { "Invalid remote profile identifier" }
            },
            profileName = profileName.orEmpty().trim().take(ShovelWorkflowLimits.MAX_NAME_LENGTH),
        )
    }

    companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,120}")

        fun local(path: String): ShovelLocationRef = ShovelLocationRef(ShovelLocationKind.LOCAL, path).normalized()

        fun remote(profileId: String, profileName: String, path: String): ShovelLocationRef = ShovelLocationRef(
            kind = ShovelLocationKind.REMOTE,
            path = path,
            profileId = profileId,
            profileName = profileName,
        ).normalized()
    }
}

enum class ShovelSourceKind { FILE_SYSTEM, ARCHIVE_ENTRY }

data class ShovelSourceRef(
    val location: ShovelLocationRef,
    val displayName: String,
    val kind: ShovelSourceKind = ShovelSourceKind.FILE_SYSTEM,
    val archiveEntryPath: String? = null,
) {
    fun normalized(): ShovelSourceRef {
        val safeName = displayName.trim().take(ShovelWorkflowLimits.MAX_NAME_LENGTH)
        require(safeName.isNotBlank()) { "Source name is required" }
        val normalizedLocation = location.normalized()
        return when (kind) {
            ShovelSourceKind.FILE_SYSTEM -> copy(
                location = normalizedLocation,
                displayName = safeName,
                archiveEntryPath = null,
            )
            ShovelSourceKind.ARCHIVE_ENTRY -> {
                require(normalizedLocation.kind == ShovelLocationKind.LOCAL) {
                    "Archive entries require a locally available archive"
                }
                val entry = normalizeArchiveEntry(requireNotNull(archiveEntryPath))
                copy(location = normalizedLocation, displayName = safeName, archiveEntryPath = entry)
            }
        }
    }

    private fun normalizeArchiveEntry(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.length <= ShovelWorkflowLimits.MAX_PATH_LENGTH) {
            "Invalid archive entry path"
        }
        require('\u0000' !in normalized) { "Invalid archive entry path" }
        val pieces = normalized.split('/')
        require(pieces.none { it.isBlank() || it == "." || it == ".." }) { "Unsafe archive entry path" }
        return pieces.joinToString("/")
    }
}

data class ShovelDestinationRef(
    val location: ShovelLocationRef,
    val required: Boolean = true,
) {
    fun normalized(): ShovelDestinationRef = copy(location = location.normalized())
}

data class ShovelPlanDefinition(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
    val sources: List<ShovelSourceRef>,
    val destinations: List<ShovelDestinationRef>,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
    val verification: TransferVerification = TransferVerification.SIZE,
    val failurePolicy: TransferFailurePolicy = TransferFailurePolicy.STOP,
    val deleteSourcesAfterVerifiedCopies: Boolean = false,
) {
    fun normalized(nowMillis: Long = System.currentTimeMillis()): ShovelPlanDefinition {
        require(schemaVersion == 1) { "Unsupported Shovel Plan version" }
        require(id.matches(SAFE_ID)) { "Invalid Shovel Plan identifier" }
        val safeName = name.trim().take(ShovelWorkflowLimits.MAX_NAME_LENGTH)
        require(safeName.isNotBlank()) { "Plan name is required" }
        require(sources.isNotEmpty() && sources.size <= ShovelWorkflowLimits.MAX_SOURCE_ROOTS) {
            "Shovel Plan must contain 1–${ShovelWorkflowLimits.MAX_SOURCE_ROOTS} sources"
        }
        require(destinations.isNotEmpty() && destinations.size <= ShovelWorkflowLimits.MAX_DESTINATIONS) {
            "Shovel Plan must contain 1–${ShovelWorkflowLimits.MAX_DESTINATIONS} destinations"
        }
        require(conflictPolicy != ConflictPolicy.ASK) { "Resolve conflicts before saving the Shovel Plan" }
        require(!deleteSourcesAfterVerifiedCopies || failurePolicy == TransferFailurePolicy.STOP) {
            "Source deletion requires stop-on-error"
        }
        require(conflictPolicy != ConflictPolicy.REPLACE || failurePolicy == TransferFailurePolicy.STOP) {
            "Safe replacement requires stop-on-error"
        }
        require(!deleteSourcesAfterVerifiedCopies || destinations.any(ShovelDestinationRef::required)) {
            "Source deletion requires at least one required destination"
        }
        val normalizedSources = sources.map(ShovelSourceRef::normalized).distinctBy { source -> source.identityKey() }
        val normalizedDestinations = destinations.map(ShovelDestinationRef::normalized)
            .distinctBy { it.location.identityKey() }
        require(normalizedSources.isNotEmpty() && normalizedDestinations.isNotEmpty()) {
            "Shovel Plan became empty after normalization"
        }
        return copy(
            name = safeName,
            updatedAtMillis = nowMillis.coerceAtLeast(createdAtMillis),
            sources = normalizedSources,
            destinations = normalizedDestinations,
        )
    }

    private fun ShovelSourceRef.identityKey(): String = "${kind.name}:${location.identityKey()}:${archiveEntryPath.orEmpty()}"

    companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9-]{1,80}")
    }
}

fun ShovelLocationRef.identityKey(): String = when (kind) {
    ShovelLocationKind.LOCAL -> "local:${File(path).canonicalPath}"
    ShovelLocationKind.REMOTE -> "remote:${profileId.orEmpty()}:${RemotePath.normalize(path)}"
}

enum class ShovelPreflightDisposition {
    READY,
    VERIFIED_IDENTICAL,
    POSSIBLY_IDENTICAL,
    KEEP_BOTH,
    SKIP,
    REPLACE,
    BLOCKED,
}

data class ShovelNodeSnapshot(
    val location: ShovelLocationRef,
    val name: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long?,
    val sha256: String? = null,
)

data class ShovelPlannedEntry(
    val index: Int,
    val sourceRootIndex: Int,
    val relativePath: String,
    val source: ShovelNodeSnapshot,
    val depth: Int,
)

data class ShovelDestinationProjection(
    val destinationIndex: Int,
    val sourceRootIndex: Int,
    val requestedRootName: String,
    val resolvedRootName: String,
    val disposition: ShovelPreflightDisposition,
    val targetRoot: ShovelLocationRef,
    val conflictSummary: String? = null,
)

data class ShovelPreflightSummary(
    val schemaVersion: Int = 1,
    val executionId: String = UUID.randomUUID().toString(),
    val plan: ShovelPlanDefinition,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val entries: List<ShovelPlannedEntry>,
    val projections: List<ShovelDestinationProjection>,
    val totalSourceBytes: Long,
    val projectedWriteBytes: Long,
    val readyCopies: Int,
    val conflicts: Int,
    val verifiedIdentical: Int,
    val possiblyIdentical: Int,
    val skipped: Int,
    val blockers: List<String>,
    val warnings: List<String>,
) {
    val canRun: Boolean
        get() = blockers.isEmpty() && entries.isNotEmpty() && (
            readyCopies > 0 ||
                plan.deleteSourcesAfterVerifiedCopies && projections.any {
                    it.disposition in setOf(
                        ShovelPreflightDisposition.VERIFIED_IDENTICAL,
                        ShovelPreflightDisposition.POSSIBLY_IDENTICAL,
                    )
                }
            )
}

enum class ShovelExecutionStatus {
    PREVIEWED,
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

data class ShovelExecutionState(
    val schemaVersion: Int = 1,
    val executionId: String,
    val status: ShovelExecutionStatus = ShovelExecutionStatus.PREVIEWED,
    val nextProjectionIndex: Int = 0,
    val nextEntryIndex: Int = 0,
    val completedFiles: Int = 0,
    val completedBytes: Long = 0,
    val failedItems: Int = 0,
    val errors: List<String> = emptyList(),
    val sourcesDeleted: Boolean = false,
    /** Strong proofs are durable and source deletion may safely resume item by item. */
    val sourceDeletionPrepared: Boolean = false,
    /** Deterministic recovery backup for an interrupted root replacement. */
    val activeBackup: ShovelLocationRef? = null,
    val attempt: Int = 0,
    val startedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val finishedAtMillis: Long? = null,
)

enum class ShovelReceiptItemStatus { COPIED, VERIFIED_IDENTICAL, SKIPPED, REPLACED, FAILED, DELETED, RESTORED }

data class ShovelReceiptItem(
    val source: ShovelLocationRef,
    val destination: ShovelLocationRef?,
    val directory: Boolean,
    val status: ShovelReceiptItemStatus,
    val sizeBytes: Long,
    val sha256: String? = null,
    val backup: ShovelLocationRef? = null,
    val errorCode: String? = null,
)

data class ShovelOperationReceipt(
    val schemaVersion: Int = 1,
    val id: String,
    val planId: String,
    val planName: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val status: ShovelExecutionStatus,
    val verification: TransferVerification,
    val items: List<ShovelReceiptItem>,
    val sourceDeletionRequested: Boolean,
    val sourcesDeleted: Boolean,
    val undoAvailable: Boolean,
    val errorCount: Int,
    val errors: List<String> = emptyList(),
)

enum class ShovelUndoDisposition { SAFE, CHANGED, MISSING, UNSUPPORTED }

data class ShovelUndoPreviewItem(
    val receiptItemIndex: Int,
    val location: ShovelLocationRef,
    val disposition: ShovelUndoDisposition,
    val detail: String,
)

data class ShovelUndoPreview(
    val receiptId: String,
    val items: List<ShovelUndoPreviewItem>,
) {
    val canRun: Boolean get() = items.isNotEmpty() && items.all { it.disposition == ShovelUndoDisposition.SAFE }
}

enum class ShovelAutomationSchedule { MANUAL_ONLY, EVERY_6_HOURS, DAILY, WEEKLY }

data class ShovelAutomationRule(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val planId: String,
    val enabled: Boolean = false,
    val schedule: ShovelAutomationSchedule = ShovelAutomationSchedule.MANUAL_ONLY,
    val unmeteredOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val requireFreshPreview: Boolean = true,
    val lastPreviewFingerprint: String? = null,
    val lastPreviewAtMillis: Long? = null,
    val lastRunAtMillis: Long? = null,
    val lastStatus: String? = null,
) {
    fun normalized(): ShovelAutomationRule {
        require(schemaVersion == 1) { "Unsupported automation rule version" }
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid automation rule identifier" }
        require(planId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid Shovel Plan identifier" }
        val safeName = name.trim().take(ShovelWorkflowLimits.MAX_NAME_LENGTH)
        require(safeName.isNotBlank()) { "Automation name is required" }
        require(!enabled || schedule != ShovelAutomationSchedule.MANUAL_ONLY) {
            "A manual-only rule cannot run automatically"
        }
        require(lastStatus == null || lastStatus.length <= 240) { "Automation status is too long" }
        return copy(name = safeName, lastStatus = lastStatus?.take(240))
    }
}
