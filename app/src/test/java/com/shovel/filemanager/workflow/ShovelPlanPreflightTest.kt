package com.shovel.filemanager.workflow

import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShovelPlanPreflightTest {
    @Test
    fun keepBothReservesUniqueNamesForSourcesFromDifferentServers() = runTest {
        val storage = FakeShovelStorage()
        val first = storage.file("/one/report.txt", "one", "one")
        val second = storage.file("/two/report.txt", "two", "two")
        val destination = storage.directory("/inbox", "target")
        val preview = ShovelPlanPreflight().preview(
            plan(
                sources = listOf(source(first, "report.txt"), source(second, "report.txt")),
                destinations = listOf(ShovelDestinationRef(destination)),
            ),
            storage,
        )

        assertTrue(preview.blockers.toString(), preview.canRun)
        assertEquals(2, preview.projections.size)
        assertNotEquals(preview.projections[0].resolvedRootName, preview.projections[1].resolvedRootName)
        assertEquals("report (1).txt", preview.projections[1].resolvedRootName)
    }

    @Test
    fun nestedDifferentFileBlocksFolderMergeDuringPreview() = runTest {
        val storage = FakeShovelStorage()
        val sourceRoot = storage.directory("/source", "one")
        storage.file("/source/readme.txt", "new", "one")
        val destination = storage.directory("/target", "two")
        storage.directory("/target/source", "two")
        storage.file("/target/source/readme.txt", "old", "two")

        val preview = ShovelPlanPreflight().preview(
            plan(
                sources = listOf(source(sourceRoot, "source")),
                destinations = listOf(ShovelDestinationRef(destination)),
                conflict = ConflictPolicy.MERGE,
                verification = TransferVerification.SHA256,
            ),
            storage,
        )

        assertFalse(preview.canRun)
        assertTrue(preview.blockers.any { it.contains("Different file content") })
    }

    @Test
    fun overlappingSourceTreesAndDestinationTreesAreRejected() = runTest {
        val storage = FakeShovelStorage()
        val parent = storage.directory("/source")
        val child = storage.directory("/source/child")
        storage.file("/source/child/a.txt", "a")
        val destination = storage.directory("/target")
        val nestedDestination = storage.directory("/target/nested")

        val preview = ShovelPlanPreflight().preview(
            plan(
                sources = listOf(source(parent, "source"), source(child, "child")),
                destinations = listOf(ShovelDestinationRef(destination), ShovelDestinationRef(nestedDestination)),
            ),
            storage,
        )

        assertFalse(preview.canRun)
        assertTrue(preview.blockers.any { it.startsWith("Source selections overlap") })
        assertTrue(preview.blockers.any { it.startsWith("Destination folders overlap") })
    }

    @Test
    fun aFileAlreadyIdenticalAtEveryRequiredDestinationCanStillBeSafelyMoved() = runTest {
        val storage = FakeShovelStorage()
        val source = storage.file("/out/report.txt", "same", "phone")
        val destination = storage.directory("/in", "server")
        storage.file("/in/report.txt", "same", "server")

        val preview = ShovelPlanPreflight().preview(
            plan(
                sources = listOf(source(source, "report.txt")),
                destinations = listOf(ShovelDestinationRef(destination)),
                verification = TransferVerification.SHA256,
                deleteSources = true,
            ),
            storage,
        )

        assertTrue(preview.blockers.toString(), preview.canRun)
        assertEquals(0, preview.readyCopies)
        assertEquals(ShovelPreflightDisposition.VERIFIED_IDENTICAL, preview.projections.single().disposition)
    }

    @Test
    fun replacingDataCannotUseSkipAndContinue() {
        val invalid = plan(
            sources = listOf(ShovelSourceRef(FakeShovelStorage().location("/a"), "a")),
            destinations = listOf(ShovelDestinationRef(FakeShovelStorage().location("/b"))),
            conflict = ConflictPolicy.REPLACE,
            failure = TransferFailurePolicy.SKIP_AND_CONTINUE,
        )
        val error = runCatching { invalid.normalized() }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("stop-on-error"))
    }

    @Test
    fun tenThousandFileFolderProducesABoundedRunnablePreview() = runTest {
        val storage = FakeShovelStorage()
        val sourceRoot = storage.directory("/large", "phone")
        repeat(10_000) { index ->
            storage.file("/large/file-${index.toString().padStart(5, '0')}.txt", byteArrayOf((index % 251).toByte()), "phone")
        }
        val destination = storage.directory("/backup", "server")

        val preview = ShovelPlanPreflight().preview(
            plan(
                sources = listOf(source(sourceRoot, "large")),
                destinations = listOf(ShovelDestinationRef(destination)),
            ),
            storage,
        )

        assertTrue(preview.blockers.toString(), preview.canRun)
        assertEquals(10_001, preview.entries.size)
        assertEquals(1, preview.projections.size)
        assertEquals(10_000, preview.totalSourceBytes)
        assertTrue(preview.warnings.isEmpty())
    }

    @Test
    fun insufficientPrivateStagingSpaceBlocksBeforeCopyStarts() = runTest {
        val storage = FakeShovelStorage().apply {
            stagingBytes = ShovelWorkflowLimits.MIN_STAGING_RESERVE_BYTES + 3L
        }
        val source = storage.file("/out/video.bin", ByteArray(4), "phone")
        val destination = storage.directory("/backup", "server")

        val preview = ShovelPlanPreflight().preview(
            plan(
                sources = listOf(source(source, "video.bin")),
                destinations = listOf(ShovelDestinationRef(destination)),
            ),
            storage,
        )

        assertFalse(preview.canRun)
        assertTrue(preview.blockers.any { it.contains("staging space") })
        assertTrue(storage.installed.isEmpty())
    }

    private fun plan(
        sources: List<ShovelSourceRef>,
        destinations: List<ShovelDestinationRef>,
        conflict: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        verification: TransferVerification = TransferVerification.SIZE,
        failure: TransferFailurePolicy = TransferFailurePolicy.STOP,
        deleteSources: Boolean = false,
    ) = ShovelPlanDefinition(
        id = "test-plan",
        name = "Test",
        sources = sources,
        destinations = destinations,
        conflictPolicy = conflict,
        verification = verification,
        failurePolicy = failure,
        deleteSourcesAfterVerifiedCopies = deleteSources,
    )

    private fun source(location: ShovelLocationRef, name: String) = ShovelSourceRef(location, name)
}
