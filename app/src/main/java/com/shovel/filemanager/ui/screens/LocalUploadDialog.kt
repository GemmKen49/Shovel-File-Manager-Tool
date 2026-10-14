package com.shovel.filemanager.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.data.FileEntryOrdering
import com.shovel.filemanager.data.OptionalFeature
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.model.SortDirection
import com.shovel.filemanager.model.SortMode
import com.shovel.filemanager.ui.components.SelectionActionBar
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.components.ShovelPullToRefresh
import com.shovel.filemanager.ui.components.featureVisible
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.uiText
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import com.shovel.filemanager.ui.theme.ShovelCard as Card
import com.shovel.filemanager.ui.theme.ShovelDropdownMenu as DropdownMenu
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val MAX_LOCAL_UPLOAD_SELECTION = 1_000

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalUploadDialog(
    initialDirectoryPath: String,
    remotePath: String,
    initialEntries: List<FileEntry>,
    initiallySelected: Set<String>,
    loadDirectory: suspend (String) -> Result<List<FileEntry>>,
    onDismiss: () -> Unit,
    onCopy: (List<String>) -> Unit,
    title: String = "Pasirinkti failus",
    confirmLabel: String = "Kopijuoti",
    filesOnly: Boolean = false,
    allowMultiple: Boolean = true,
    selectionLimit: Int = MAX_LOCAL_UPLOAD_SELECTION,
    parentDirectory: (String) -> String? = { File(it).parentFile?.absolutePath },
    message: String? = null,
    confirming: Boolean = false,
) {
    val maxSelection = if (allowMultiple) selectionLimit.coerceIn(1, MAX_LOCAL_UPLOAD_SELECTION) else 1
    var navigation by remember(initialDirectoryPath, remotePath) {
        mutableStateOf(LocalUploadNavigationState(initialDirectoryPath))
    }
    val currentPath = navigation.currentPath
    var entries by remember(initialDirectoryPath, remotePath) { mutableStateOf(initialEntries) }
    var searchQuery by remember(currentPath) { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var pickerMenu by remember { mutableStateOf(false) }
    var showOnlyFolders by remember { mutableStateOf(false) }
    var showOnlyFiles by remember { mutableStateOf(false) }
    var pickerSort by remember { mutableStateOf(SortMode.NAME) }
    var displayedEntries by remember(currentPath) { mutableStateOf(initialEntries) }
    LaunchedEffect(currentPath, entries, searchQuery, showOnlyFolders, showOnlyFiles, pickerSort) {
        val source = entries
        val query = searchQuery.trim().take(200)
        displayedEntries = withContext(Dispatchers.Default) {
            FileEntryOrdering.order(source.filter {
                ensureActive()
                (query.isBlank() || it.name.contains(query, ignoreCase = true)) &&
                    (!showOnlyFolders || it.isDirectory) && (!showOnlyFiles || !it.isDirectory)
            }, pickerSort, SortDirection.ASCENDING)
        }
    }
    var loading by remember(initialDirectoryPath, remotePath) { mutableStateOf(false) }
    var error by remember(initialDirectoryPath, remotePath) { mutableStateOf<String?>(null) }
    var refreshToken by remember(initialDirectoryPath, remotePath) { mutableStateOf(0) }
    var selected: Set<String> by remember(initialDirectoryPath, remotePath) {
        mutableStateOf(initiallySelected.take(maxSelection).toCollection(linkedSetOf()))
    }

    LaunchedEffect(currentPath, refreshToken) {
        loading = true
        error = null
        if (currentPath != initialDirectoryPath) entries = emptyList()
        loadDirectory(currentPath).fold(
            onSuccess = { loaded -> entries = loaded },
            onFailure = { failure ->
                entries = emptyList()
                error = failure.message ?: "Katalogo atverti nepavyko"
            },
        )
        loading = false
    }

    fun toggle(path: String) {
        selected = if (path in selected) {
            selected - path
        } else if (!allowMultiple) {
            setOf(path)
        } else if (selected.size < maxSelection) {
            selected + path
        } else {
            selected
        }
    }

    fun navigateTo(path: String) {
        navigation = navigation.navigateTo(path)
    }

    fun navigateBack() {
        navigation = navigation.navigateBack()
    }

    val parentPath = parentDirectory(currentPath)
    val visiblePaths = remember(displayedEntries, filesOnly, maxSelection) {
        displayedEntries.asSequence().filter { !filesOnly || !it.isDirectory }
            .take(maxSelection).map(FileEntry::absolutePath).toList()
    }
    val allSelected = visiblePaths.isNotEmpty() && visiblePaths.all(selected::contains)

    com.shovel.filemanager.ui.components.ShovelModalDialog(
        onDismissRequest = { if (navigation.canNavigateBack) navigateBack() else onDismiss() },
        onClose = onDismiss,
        icon = if (filesOnly) Icons.Rounded.FolderOpen else Icons.Rounded.CloudUpload,
        title = title,
        expandedContent = true,
        modifier = Modifier.testTag("local_upload_dialog"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Atšaukti") }
            Button(
                onClick = { onCopy(selected.toList()) },
                enabled = selected.isNotEmpty() && !confirming,
            ) { Text("${uiText(confirmLabel)} (${selected.size})") }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (featureVisible(OptionalFeature.TOOLBAR_UP)) IconButton(
                    onClick = { parentPath?.let(::navigateTo) },
                    enabled = parentPath != null && !loading,
                    modifier = Modifier.testTag("local_upload_up"),
                ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Aukštyn")) }
                Text(currentPath.ifEmpty { uiText("Saugykla") }, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (searchVisible || featureVisible(OptionalFeature.TOOLBAR_SEARCH)) IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" },
                    modifier = Modifier.testTag("local_upload_search_toggle")) {
                    Icon(Icons.Rounded.Search, contentDescription = uiText(if (searchVisible) "Slėpti paiešką" else "Ieškoti"))
                }
                Box {
                    IconButton(onClick = { pickerMenu = true }, modifier = Modifier.testTag("local_upload_more")) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = uiText("Daugiau veiksmų"))
                    }
                    DropdownMenu(expanded = pickerMenu, onDismissRequest = { pickerMenu = false }) {
                        DropdownMenuItem(text = { LText("Visi elementai") }, onClick = {
                            showOnlyFolders = false; showOnlyFiles = false; pickerMenu = false
                        }, leadingIcon = if (!showOnlyFolders && !showOnlyFiles) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = null) }) else null)
                        DropdownMenuItem(text = { LText("Tik aplankai") }, onClick = {
                            showOnlyFolders = true; showOnlyFiles = false; pickerMenu = false
                        }, leadingIcon = if (showOnlyFolders) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = null) }) else null)
                        DropdownMenuItem(text = { LText("Tik failai") }, onClick = {
                            showOnlyFiles = true; showOnlyFolders = false; pickerMenu = false
                        }, leadingIcon = if (showOnlyFiles) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = null) }) else null)
                        HorizontalDivider()
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { LText(when (mode) {
                                SortMode.NAME -> "Pavadinimas"; SortMode.SIZE -> "Dydis";
                                SortMode.MODIFIED -> "Pakeista"; SortMode.TYPE -> "Tipas"
                            }) }, onClick = { pickerSort = mode; pickerMenu = false },
                                leadingIcon = if (pickerSort == mode) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = null) }) else null)
                        }
                    }
                }
            }
            if (remotePath.isNotBlank()) LText("Į: $remotePath", style = MaterialTheme.typography.bodySmall)
            if (searchVisible) OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it.take(200) },
                label = { LText("Ieškoti šiame sąraše") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("local_upload_search"),
            )
            if (selected.isNotEmpty()) SelectionActionBar(
                count = selected.size,
                allSelected = allSelected,
                onClose = { selected = emptySet() },
                onToggleSelectAll = {
                    selected = if (allSelected) selected - visiblePaths.toSet() else {
                        val available = maxSelection - selected.size
                        if (!allowMultiple) visiblePaths.take(1).toSet()
                        else selected + visiblePaths.filterNot(selected::contains).take(available)
                    }
                },
                modifier = Modifier.testTag("local_upload_selection_bar"),
            )
            message?.let { LText(it, color = MaterialTheme.colorScheme.error) }
            ShovelPullToRefresh(
                isRefreshing = loading,
                onRefresh = { refreshToken += 1 },
                modifier = Modifier.fillMaxWidth().weight(1f),
                testTag = "pull_to_refresh_local_upload",
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("local_upload_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (loading) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (error != null) {
                    item {
                        LText(
                            error.orEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (displayedEntries.isEmpty()) {
                    item { LText(if (searchQuery.isBlank()) "Katalogas tuščias" else "Atitinkančių failų nėra") }
                }
                items(displayedEntries, key = FileEntry::absolutePath) { entry ->
                    val entrySelected = entry.absolutePath in selected
                    val selectionShape = MaterialTheme.shapes.medium
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_upload_entry_${entry.absolutePath}")
                            .border(if (entrySelected) 1.5.dp else 1.dp,
                                if (entrySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                selectionShape)
                            .combinedClickable(
                                onClick = {
                                    if (entry.isDirectory && (filesOnly || selected.isEmpty())) {
                                        navigateTo(entry.absolutePath)
                                    } else {
                                        toggle(entry.absolutePath)
                                    }
                                },
                                onLongClick = { if (!filesOnly || !entry.isDirectory) toggle(entry.absolutePath) },
                            ),
                        shape = selectionShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (entrySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!filesOnly || !entry.isDirectory) Checkbox(
                                checked = entrySelected,
                                onCheckedChange = { toggle(entry.absolutePath) },
                            )
                            Icon(
                                if (entry.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                contentDescription = null,
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (entry.isDirectory) {
                                    LText("Aplankas", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Text(FileSystemRules.humanBytes(entry.sizeBytes), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (entry.isDirectory) {
                                IconButton(
                                    onClick = { navigateTo(entry.absolutePath) },
                                    modifier = Modifier.testTag("local_upload_open_${entry.absolutePath}"),
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = uiText("Atidaryti aplanką"))
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

internal data class LocalUploadNavigationState(
    val currentPath: String,
    val backStack: List<String> = emptyList(),
) {
    val canNavigateBack: Boolean get() = backStack.isNotEmpty()

    fun navigateTo(path: String): LocalUploadNavigationState = if (path == currentPath) {
        this
    } else {
        copy(currentPath = path, backStack = (backStack + currentPath).takeLast(64))
    }

    fun navigateBack(): LocalUploadNavigationState = backStack.lastOrNull()?.let { previous ->
        copy(currentPath = previous, backStack = backStack.dropLast(1))
    } ?: this
}

