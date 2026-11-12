package com.shovel.filemanager.ui.components

import com.shovel.filemanager.ui.localization.LText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import androidx.compose.material3.CircularProgressIndicator
import com.shovel.filemanager.ui.theme.ShovelFilterChip as FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shovel.filemanager.operations.BatchRenamePreviewItem
import com.shovel.filemanager.operations.RenameCaseMode
import com.shovel.filemanager.ui.MainViewModel

@Composable
fun BatchRenameDialog(viewModel: MainViewModel) {
    val state by viewModel.batchRename.collectAsStateWithLifecycle()
    if (!state.open) return
    val spec = state.spec
    var numberStartText by remember(state.sourcePaths) { mutableStateOf(spec.numberStart.toString()) }
    var numberPaddingText by remember(state.sourcePaths) { mutableStateOf(spec.numberPadding.toString()) }
    val numberInputsValid = !spec.numberingEnabled || (
        numberStartText.toIntOrNull()?.let { it in 0..999_999_999 } == true &&
            numberPaddingText.toIntOrNull()?.let { it in 1..9 } == true
    )

    ShovelModalDialog(
        title = "Batch rename",
        subtitle = "Changes run only after you confirm the plan",
        icon = Icons.Rounded.DriveFileRenameOutline,
        onDismissRequest = viewModel::closeBatchRename,
        expandedContent = true,
        modifier = Modifier.testTag("batch_rename_dialog"),
        actions = {
            TextButton(onClick = viewModel::closeBatchRename) { LText("Cancel") }
            Button(
                onClick = viewModel::executeBatchRename,
                enabled = numberInputsValid && !state.running && state.preview?.canExecute == true,
                modifier = Modifier.testTag("batch_rename_execute"),
            ) {
                LText("Rename ${state.preview?.changedCount ?: 0}")
            }
        },
    ) {
        LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("batch_rename_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            OutlinedTextField(
                                value = spec.findText,
                                onValueChange = { viewModel.updateBatchRenameSpec(spec.copy(findText = it)) },
                                modifier = Modifier.fillMaxWidth().testTag("batch_rename_find"),
                                label = { LText("Find in name") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = spec.replacementText,
                                onValueChange = { viewModel.updateBatchRenameSpec(spec.copy(replacementText = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { LText("Replace with") },
                                singleLine = true,
                            )
                            FilterChip(
                                selected = spec.useRegex,
                                onClick = { viewModel.updateBatchRenameSpec(spec.copy(useRegex = !spec.useRegex)) },
                                label = { LText("Regular expression") },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = spec.prefix,
                                    onValueChange = { viewModel.updateBatchRenameSpec(spec.copy(prefix = it)) },
                                    modifier = Modifier.weight(1f).testTag("batch_rename_prefix"),
                                    label = { LText("Prefix") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = spec.suffix,
                                    onValueChange = { viewModel.updateBatchRenameSpec(spec.copy(suffix = it)) },
                                    modifier = Modifier.weight(1f),
                                    label = { LText("Suffix") },
                                    singleLine = true,
                                )
                            }
                            LText("Letter case", style = MaterialTheme.typography.labelLarge)
                            ShovelActionRow {
                                RenameCaseMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = spec.caseMode == mode,
                                        onClick = { viewModel.updateBatchRenameSpec(spec.copy(caseMode = mode)) },
                                        label = { LText(caseModeLabel(mode)) },
                                    )
                                }
                            }
                            FilterChip(
                                selected = spec.numberingEnabled,
                                onClick = { viewModel.updateBatchRenameSpec(spec.copy(numberingEnabled = !spec.numberingEnabled)) },
                                label = { LText("Add numbering") },
                            )
                            if (spec.numberingEnabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = numberStartText,
                                        onValueChange = { raw ->
                                            numberStartText = raw.filter(Char::isDigit).take(9)
                                            numberStartText.toIntOrNull()?.let { value ->
                                                viewModel.updateBatchRenameSpec(spec.copy(numberStart = value))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        label = { LText("Start") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        isError = numberStartText.toIntOrNull()?.let { it !in 0..999_999_999 } != false,
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = numberPaddingText,
                                        onValueChange = { raw ->
                                            numberPaddingText = raw.filter(Char::isDigit).take(1)
                                            numberPaddingText.toIntOrNull()?.takeIf { it in 1..9 }?.let { value ->
                                                viewModel.updateBatchRenameSpec(spec.copy(numberPadding = value))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        label = { LText("Digits") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        isError = numberPaddingText.toIntOrNull()?.let { it !in 1..9 } != false,
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = spec.numberSeparator,
                                        onValueChange = { viewModel.updateBatchRenameSpec(spec.copy(numberSeparator = it.take(8))) },
                                        modifier = Modifier.weight(1f),
                                        label = { LText("Separator") },
                                        singleLine = true,
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = spec.extensionOverride,
                                onValueChange = { viewModel.updateBatchRenameSpec(spec.copy(extensionOverride = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { LText("New file extension (optional)") },
                                supportingText = { LText("An empty value keeps the current extension; folders are unaffected") },
                                singleLine = true,
                            )
                        }
                    }

                    item {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LText("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (state.running) CircularProgressIndicator(modifier = Modifier.padding(3.dp))
                            state.preview?.let { LText("Keisis ${it.changedCount} / ${it.items.size}") }
                        }
                    }

                    state.error?.let { error ->
                        item { ErrorText(error) }
                    }
                    if (!numberInputsValid) {
                        item { ErrorText("Check the numbering start and digit count") }
                    }
                    state.preview?.errors?.forEach { error ->
                        item(key = "global:$error") { ErrorText(error) }
                    }
                    state.preview?.let { preview ->
                        items(preview.items, key = BatchRenamePreviewItem::originalPath) { item ->
                            RenamePreviewRow(item)
                        }
                    }
        }
    }
}

@Composable
private fun RenamePreviewRow(item: BatchRenamePreviewItem) {
    val issue = item.issue
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.changed) {
            Text(
                "→ ${item.targetName}",
                color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            LText(
                "→ Unchanged",
                color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (issue != null) LText(issue, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun ErrorText(text: String) {
    LText(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun caseModeLabel(mode: RenameCaseMode): String = when (mode) {
    RenameCaseMode.KEEP -> "Unchanged"
    RenameCaseMode.LOWERCASE -> "lowercase"
    RenameCaseMode.UPPERCASE -> "UPPERCASE"
}
