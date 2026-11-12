package com.shovel.filemanager.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.apk.ApkInstallMetadata
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ApkInstallConfirmationDialog(
    cacheKey: Any,
    load: suspend () -> ApkInstallMetadata,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val result by produceState<Result<ApkInstallMetadata>?>(null, cacheKey) {
        value = withContext(Dispatchers.IO) { runCatching { load() } }
    }
    ShovelModalDialog(
        title = "Confirm app installation", icon = Icons.Rounded.InstallMobile,
        onDismissRequest = onDismiss, modifier = Modifier.testTag("apk_install_confirmation"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Cancel") }
            Button(onClick = onInstall, enabled = result?.isSuccess == true,
                modifier = Modifier.testTag("confirm_apk_install")) { LText("Install") }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                result == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                result?.isFailure == true -> LText(result?.exceptionOrNull()?.message ?: "APK information unavailable",
                    color = MaterialTheme.colorScheme.error)
                else -> result?.getOrNull()?.let { info ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        info.icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(info.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(info.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MetadataLine("Version to install", info.candidateVersion)
                    MetadataLine("Installed version", info.installedVersion ?: "Not installed")
                    MetadataLine("File size", FileSystemRules.humanBytes(info.fileBytes))
                    MetadataLine("Minimali Android API", info.minimumSdk?.toString() ?: "Unknown")
                    MetadataLine("Target Android API", info.targetSdk?.toString() ?: "Unknown")
                    MetadataLine("Processor architectures", info.abis.ifEmpty { listOf("Universal / no native libraries") }.joinToString())
                    LText("The APK does not specify a reliable maximum Android version; the developer's target API is shown.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LText("Android always confirms installation. The app cannot install silently.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        LText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
