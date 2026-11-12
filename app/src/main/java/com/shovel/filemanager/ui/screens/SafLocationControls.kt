package com.shovel.filemanager.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.data.SafLocation
import com.shovel.filemanager.data.SafLocationRules
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.uiText
import com.shovel.filemanager.ui.theme.ShovelAlertDialog
import com.shovel.filemanager.ui.theme.ShovelButton

@Composable
internal fun SafLocationActionButtons(
    location: SafLocation,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val key = location.uri.hashCode().toUInt().toString(16)
    IconButton(onClick = onEdit, modifier = Modifier.testTag("saf_location_edit_$key")) {
        Icon(Icons.Rounded.Edit, contentDescription = uiText("Edit connection"))
    }
    IconButton(onClick = onRemove, modifier = Modifier.testTag("saf_location_remove_$key")) {
        Icon(Icons.Rounded.Delete, contentDescription = uiText("Remove location"))
    }
}

@Composable
internal fun SafLocationEditDialog(
    location: SafLocation,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by remember(location.uri, location.title) { mutableStateOf(location.title) }
    val normalized = remember(title) { runCatching { SafLocationRules.normalizeTitle(title) }.getOrNull() }
    ShovelModalDialog(
        title = "Edit connection",
        icon = Icons.Rounded.Edit,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("saf_location_edit_dialog"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Cancel") }
            ShovelButton(onClick = { normalized?.let(onSave) }, enabled = normalized != null) { LText("Save") }
        },
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { LText("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)
                .testTag("saf_location_title"),
        )
    }
}

@Composable
internal fun SafLocationRemovalDialog(
    location: SafLocation,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    ShovelAlertDialog(
        onDismissRequest = onDismiss,
        title = { LText("Remove the selected location?") },
        text = { LText("Shovel will lose its long-term permission for \"${location.title}\". Your files will not be deleted.") },
        confirmButton = { ShovelButton(onClick = onRemove) { LText("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { LText("Cancel") } },
        modifier = Modifier.testTag("saf_location_remove_dialog"),
    )
}
