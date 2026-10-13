package com.shovel.filemanager.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import com.shovel.filemanager.ui.theme.ShovelAlertDialog as AlertDialog
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.editing.EditDestinationRules
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.uiText
import java.io.File

private data class SaveAsNavigation(
    val path: String,
    val backStack: List<String> = emptyList(),
) {
    fun navigate(target: String): SaveAsNavigation = if (target == path) this else copy(
        path = target,
        backStack = (backStack + path).takeLast(100),
    )

    fun back(): SaveAsNavigation = backStack.lastOrNull()?.let { previous ->
        copy(path = previous, backStack = backStack.dropLast(1))
    } ?: this
}

@Composable
fun EditSaveAsDialog(
    initialFileName: String,
    initialLocalPath: String,
    loadLocalDirectory: suspend (String) -> Result<List<FileEntry>>,
    onSaveLocal: (String, String) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    var navigation by remember(initialLocalPath) { mutableStateOf(SaveAsNavigation(initialLocalPath)) }
    var name by remember(initialFileName) { mutableStateOf(initialFileName) }
    var directories by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentPath = navigation.path

    LaunchedEffect(currentPath) {
        loading = true
        error = null
        directories = emptyList()
        loadLocalDirectory(currentPath).map { entries ->
            entries.asSequence()
                .filter(FileEntry::isDirectory)
                .take(MAX_VISIBLE_DIRECTORIES)
                .map { it.name to it.absolutePath }
                .toList()
        }.fold(
            onSuccess = { directories = it },
            onFailure = { failure -> error = failure.message ?: "Katalogo atidaryti nepavyko" },
        )
        loading = false
    }

    fun navigateTo(path: String) {
        navigation = navigation.navigate(path)
    }

    fun navigateBackOrDismiss() {
        if (navigation.backStack.isNotEmpty()) navigation = navigation.back() else onDismiss()
    }

    val parentPath = remember(currentPath) { File(currentPath).parentFile?.absolutePath }
    val validatedName = runCatching { EditDestinationRules.validateFileName(name) }.getOrNull()

    AlertDialog(
        onDismissRequest = ::navigateBackOrDismiss,
        icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
        title = { LText("Išsaugoti kaip") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { parentPath?.let(::navigateTo) }, enabled = parentPath != null && !loading) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Aukštyn"))
                    }
                    Text(
                        currentPath,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
                when {
                    loading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                    error != null -> LText(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    directories.isEmpty() -> LText("Šiame kataloge nėra poaplankių", style = MaterialTheme.typography.bodySmall)
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(directories, key = { it.second }) { (directoryName, directoryPath) ->
                            ListItem(
                                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                headlineContent = { Text(directoryName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth().clickable { navigateTo(directoryPath) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(255) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { LText("Failo pavadinimas") },
                    singleLine = true,
                    isError = name.isNotEmpty() && validatedName == null,
                    supportingText = if (name.isNotEmpty() && validatedName == null) {
                        { LText("Pavadinime negali būti kelio ar valdymo simbolių") }
                    } else null,
                )
                OutlinedButton(
                    onClick = onOpenSystemPicker,
                    modifier = Modifier.fillMaxWidth(),
                ) { LText("Rinktis per Android dokumentų sistemą") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeName = validatedName ?: return@Button
                    onSaveLocal(currentPath, safeName)
                },
                enabled = validatedName != null && !loading,
            ) { LText("Išsaugoti čia") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { LText("Atšaukti") } },
    )
}

private const val MAX_VISIBLE_DIRECTORIES = 10_000
