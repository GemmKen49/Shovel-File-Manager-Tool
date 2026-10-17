package com.shovel.filemanager.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.ui.localization.LText

/** Asks for the password of an encrypted archive before its contents can be listed. */
@Composable
fun ArchivePasswordDialog(
    archiveName: String,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember(archiveName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {
            password = ""
            onDismiss()
        },
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { LText("Password required") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(archiveName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { LText("Archive password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("archive_password_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val secret = password.toCharArray()
                    password = ""
                    onConfirm(secret)
                },
                enabled = password.isNotEmpty(),
            ) { LText("Open") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    password = ""
                    onDismiss()
                },
            ) { LText("Cancel") }
        },
    )
}
