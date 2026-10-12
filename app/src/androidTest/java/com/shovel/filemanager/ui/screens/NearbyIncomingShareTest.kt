package com.shovel.filemanager.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.shovel.filemanager.AFFileManagerApplication
import com.shovel.filemanager.ui.IncomingShareUiState
import com.shovel.filemanager.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class NearbyIncomingShareTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startingTransferHandsThePrivateCopyToTheServiceBeforeClosingTheDialog() {
        val app = ApplicationProvider.getApplicationContext<AFFileManagerApplication>()
        val source = File(app.cacheDir, "forward-${System.nanoTime()}.txt").apply { writeText("forwarded from another app") }
        val destination = File(app.cacheDir, "receive-${System.nanoTime()}").apply { mkdir() }
        val stageRoot = File(app.cacheDir, "nearby-send-staging")
        val before = stageRoot.listFiles().orEmpty().map(File::getName).toSet()
        val address = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .first { it is java.net.Inet4Address && it.isSiteLocalAddress }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", source)
        val request = mutableStateOf<IncomingShareUiState?>(IncomingShareUiState(789L, listOf(uri)))
        val visible = mutableStateOf(true)
        val store = ViewModelStore()
        try {
            com.shovel.filemanager.transfer.LanHttpServer(destination, address, requestedCode = "12345678").use { server ->
                val session = server.start()
                val vm = MainViewModel(app).also { store.put("test", it) }
                compose.setContent { MaterialTheme {
                    if (visible.value) NearbySendDialog(vm, request.value, { request.value = null }, { visible.value = false })
                } }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Ready to send: 1").fetchSemanticsNodes().isNotEmpty() }
                val pairing = com.shovel.filemanager.transfer.NearbyPairing.create(session.address, session.port, session.code)
                compose.onNode(hasSetTextAction()).performTextInput(pairing.encoded())
                compose.onNodeWithText("Start transfer").performClick()
                compose.waitUntil(20_000) {
                    com.shovel.filemanager.transfer.NearbyTransferController.state.value.status in setOf(
                        com.shovel.filemanager.transfer.NearbyTransferStatus.COMPLETED,
                        com.shovel.filemanager.transfer.NearbyTransferStatus.ERROR,
                    )
                }
                val finished = com.shovel.filemanager.transfer.NearbyTransferController.state.value
                assertEquals(finished.message, com.shovel.filemanager.transfer.NearbyTransferStatus.COMPLETED, finished.status)
                assertEquals(source.readText(), File(destination, source.name).readText())
                compose.waitUntil(10_000) { stageRoot.listFiles().orEmpty().all { it.name in before } }
                assertTrue(com.shovel.filemanager.transfer.NearbyTransferController.state.value.files.all { it.localPath == null })
            }
        } finally {
            compose.runOnUiThread { store.clear() }
            com.shovel.filemanager.transfer.LanTransferController.stop(app)
            com.shovel.filemanager.transfer.NearbyTransferController.connection.clear()
            com.shovel.filemanager.transfer.NearbyTransferController.clearFinished()
            source.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun unavailableIncomingContentSettlesAndAllowsReturningToThePicker() {
        val app = ApplicationProvider.getApplicationContext<AFFileManagerApplication>()
        val request = mutableStateOf<IncomingShareUiState?>(IncomingShareUiState(456L,
            listOf(android.net.Uri.parse("content://com.shovel.filemanager.missing.test/file.txt"))))
        val store = ViewModelStore()
        var consumed = 0
        try {
            val vm = MainViewModel(app).also { store.put("test", it) }
            compose.setContent { MaterialTheme { NearbySendDialog(vm, request.value,
                { consumed++; request.value = null }, {}) } }
            compose.waitUntil(10_000) { consumed == 1 }
            compose.onNodeWithTag("nearby_preparing").assertDoesNotExist()
            compose.onNodeWithText("Start transfer").assertIsNotEnabled()
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Choose files to send").assertIsDisplayed()
        } finally { compose.runOnUiThread { store.clear() } }
    }

    @Test fun consumingIncomingRequestDoesNotCancelPreparationAndCloseCleansPrivateCopy() {
        val app = ApplicationProvider.getApplicationContext<AFFileManagerApplication>()
        val source = File(app.cacheDir, "incoming-${System.nanoTime()}.txt").apply { writeText("shared fixture") }
        val stageRoot = File(app.cacheDir, "nearby-send-staging")
        val before = stageRoot.listFiles().orEmpty().map(File::getName).toSet()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", source)
        val request = mutableStateOf<IncomingShareUiState?>(IncomingShareUiState(123L, listOf(uri)))
        val visible = mutableStateOf(true)
        val store = ViewModelStore()
        var consumed = 0
        try {
            val vm = MainViewModel(app).also { store.put("test", it) }
            compose.setContent { MaterialTheme {
                if (visible.value) NearbySendDialog(vm, request.value, {
                    consumed++
                    request.value = null // This is what the real parent does.
                }, { visible.value = false })
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Ready to send: 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Pair the receiving phone").assertIsDisplayed()
            compose.onNodeWithText("Start transfer").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(1, consumed) }
            assertEquals("shared fixture", source.readText())
            compose.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { stageRoot.listFiles().orEmpty().all { it.name in before } }
            assertTrue(source.isFile)
        } finally {
            compose.runOnUiThread { store.clear() }
            source.delete()
        }
    }
}
