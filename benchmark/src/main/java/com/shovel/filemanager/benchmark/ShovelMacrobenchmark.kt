package com.shovel.filemanager.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.Direction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class ShovelMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private var ftp: LoopbackFtpServer? = null

    @Before
    fun prepare() {
        ShovelBenchmarkEnvironment.resetTargetData()
        ftp = LoopbackFtpServer().also { it.start() }
    }

    @After
    fun close() {
        ftp?.close()
        ftp = null
    }

    @Test
    fun coldStartAndFirstPaint() = benchmarkRule.measureRepeated(
        packageName = ShovelBenchmarkEnvironment.PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = profileCompilation(),
        startupMode = StartupMode.COLD,
        iterations = 7,
        setupBlock = { prepareTargetForColdStart() },
    ) {
        startActivityAndWait(ShovelBenchmarkEnvironment.mainIntent())
    }

    @Test
    fun largeDirectoryFirstContent() = benchmarkRule.measureRepeated(
        packageName = ShovelBenchmarkEnvironment.PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = profileCompilation(),
        startupMode = StartupMode.COLD,
        iterations = 7,
        setupBlock = { prepareTargetForColdStart() },
    ) {
        startActivityAndWait(ShovelBenchmarkEnvironment.localIntent("large"))
        ShovelBenchmarkEnvironment.run { awaitObject("file_list_content_LEFT") }
    }

    @Test
    fun largeDirectoryScroll() = scrollBenchmark("large", "file_list_LEFT")

    @Test
    fun thumbnailScroll() = scrollBenchmark("thumbnails", "file_grid_LEFT")

    @Test
    fun remoteListAndScroll() = benchmarkRule.measureRepeated(
        packageName = ShovelBenchmarkEnvironment.PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = profileCompilation(),
        startupMode = StartupMode.COLD,
        iterations = 7,
        setupBlock = { prepareTargetForColdStart() },
    ) {
        startActivityAndWait(ShovelBenchmarkEnvironment.remoteIntent())
        ShovelBenchmarkEnvironment.run {
            awaitObject("remote_list_ready")
            fling("remote_list", Direction.DOWN, times = 4)
            fling("remote_list", Direction.UP, times = 2)
        }
    }

    private fun scrollBenchmark(dataset: String, resourceName: String) = benchmarkRule.measureRepeated(
        packageName = ShovelBenchmarkEnvironment.PACKAGE_NAME,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = profileCompilation(),
        startupMode = StartupMode.WARM,
        iterations = 7,
        setupBlock = {
            ShovelBenchmarkEnvironment.prepareTarget(device)
            startActivityAndWait(ShovelBenchmarkEnvironment.localIntent(dataset))
            ShovelBenchmarkEnvironment.run { awaitObject(resourceName.contentTag()) }
            device.waitForIdle()
        },
    ) {
        ShovelBenchmarkEnvironment.run {
            fling(resourceName, Direction.DOWN, times = 4)
            fling(resourceName, Direction.UP, times = 2)
        }
    }

    private fun profileCompilation() = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable)

    private fun String.contentTag(): String =
        "${substringBeforeLast('_')}_content_${substringAfterLast('_')}"

    private fun androidx.benchmark.macro.MacrobenchmarkScope.prepareTargetForColdStart() {
        ShovelBenchmarkEnvironment.prepareTarget(device)
        killProcess()
        pressHome()
    }
}
