package com.shovel.filemanager.ui.theme

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Prevent new screens from bypassing the app-owned appearance roles. Runtime tests cover rendering. */
class AppearanceConsumerContractTest {
    private val source = File("src/main/java/com/shovel/filemanager").takeIf { it.isDirectory }
        ?: File("app/src/main/java/com/shovel/filemanager")

    @Test fun appScreensUseSharedPopupCardAndSurfaceComponents() {
        val rawImport = Regex("(?m)^import androidx\\.compose\\.material3\\.(AlertDialog|DropdownMenu|Card|ElevatedCard|Surface|FilterChip|Button)\\r?$")
        val violations = source.walkTopDown().filter { it.extension == "kt" && "/ui/theme/" !in it.invariantSeparatorsPath }
            .filter { rawImport.containsMatchIn(it.readText()) }.map { it.relativeTo(source).path }.toList()
        assertEquals("Direct Material imports bypass Shovel's global appearance contract", emptyList<String>(), violations)
    }

    @Test fun everyPageSizedBrowserUsesTheSharedPageBackground() {
        listOf("ui/screens/FileCategoryBrowserDialog.kt", "ui/screens/AdvancedStorageBrowserDialog.kt",
            "ui/screens/SafBrowserDialog.kt", "ui/screens/TrashBrowserDialog.kt", "ui/screens/CleanupReviewDialog.kt",
            "ui/screens/ShovelWorkflowDialog.kt", "ui/preview/FilePreviewDialog.kt", "ui/preview/PdfSignatureDialog.kt",
            "ui/terminal/TerminalOverlay.kt", "apk/SplitApkInstallActivity.kt").forEach {
            assertTrue(it, File(source, it).readText().contains("AppearancePage("))
        }
        val externalPicker = File(source, "picker/FilePickerActivity.kt").readText()
        assertTrue("External picker keeps the shared appearance theme", externalPicker.contains("ShovelTheme("))
        assertTrue("External picker uses the shared dialog", externalPicker.contains("LocalUploadDialog("))
        assertFalse("External picker must leave the caller visible", externalPicker.contains("AppearancePage("))
    }

    @Test fun everyDialogUsesTheScaleAwareWindowWrapper() {
        val rawImport = Regex("(?m)^import androidx\\.compose\\.ui\\.window\\.Dialog\\r?$")
        val violations = source.walkTopDown()
            .filter { it.extension == "kt" && it.invariantSeparatorsPath.substringAfterLast('/') != "AppearanceComponents.kt" }
            .filter { rawImport.containsMatchIn(it.readText()) }
            .map { it.relativeTo(source).path }
            .toList()
        assertEquals("Direct Dialog imports bypass Shovel's live interface scale", emptyList<String>(), violations)
    }
}
