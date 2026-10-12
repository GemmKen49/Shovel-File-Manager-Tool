package com.shovel.filemanager.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.Direction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private lateinit var ftp: LoopbackFtpServer

    @Before
    fun prepare() {
        ftp = LoopbackFtpServer().also { it.start() }
    }

    @After
    fun close() = ftp.close()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = ShovelBenchmarkEnvironment.PACKAGE_NAME,
        maxIterations = 3,
        stableIterations = 2,
        includeInStartupProfile = true,
        filterPredicate = { rule -> rule.startsWith("Lcom/shovel/") },
    ) {
        ShovelBenchmarkEnvironment.prepareTarget(device)
        pressHome()
        startActivityAndWait(ShovelBenchmarkEnvironment.mainIntent())
    }

    @Test
    fun criticalUserJourneys() = baselineProfileRule.collect(
        packageName = ShovelBenchmarkEnvironment.PACKAGE_NAME,
        maxIterations = 3,
        stableIterations = 2,
        includeInStartupProfile = false,
        filterPredicate = { rule -> rule.startsWith("Lcom/shovel/") },
    ) {
        ShovelBenchmarkEnvironment.prepareTarget(device)
        pressHome()
        startActivityAndWait(ShovelBenchmarkEnvironment.localIntent("large"))
        ShovelBenchmarkEnvironment.run { awaitObject("file_list_content_LEFT") }
        ShovelBenchmarkEnvironment.run { fling("file_list_LEFT", Direction.DOWN, times = 3) }

        startActivityAndWait(ShovelBenchmarkEnvironment.localIntent("thumbnails"))
        ShovelBenchmarkEnvironment.run { awaitObject("file_grid_content_LEFT") }
        ShovelBenchmarkEnvironment.run { fling("file_grid_LEFT", Direction.DOWN, times = 3) }

        startActivityAndWait(ShovelBenchmarkEnvironment.remoteIntent())
        ShovelBenchmarkEnvironment.run { awaitObject("remote_list_ready") }
        ShovelBenchmarkEnvironment.run { fling("remote_list", Direction.DOWN, times = 3) }
    }
}
