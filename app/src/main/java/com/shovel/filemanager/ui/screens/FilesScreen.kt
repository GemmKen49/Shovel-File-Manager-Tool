package com.shovel.filemanager.ui.screens

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import com.shovel.filemanager.ui.theme.ShovelAlertDialog as AlertDialog
import androidx.compose.material3.AssistChip
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import com.shovel.filemanager.ui.theme.ShovelCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.shovel.filemanager.ui.theme.ShovelDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.shovel.filemanager.ui.theme.ShovelElevatedCard as ElevatedCard
import com.shovel.filemanager.ui.theme.ShovelFilterChip as FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import com.shovel.filemanager.ui.theme.ShovelSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shovel.filemanager.archive.ArchiveFormat
import com.shovel.filemanager.archive.ArchiveCompressionRules
import com.shovel.filemanager.archive.ArchiveTargetSizeRules
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.model.ClipboardMode
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.SortMode
import com.shovel.filemanager.model.StorageRoot
import com.shovel.filemanager.model.StorageRootKind
import com.shovel.filemanager.data.PanelWorkspace
import com.shovel.filemanager.data.DirectoryDisplaySettings
import com.shovel.filemanager.data.DirectoryGridStyle
import com.shovel.filemanager.data.DirectoryLayoutMode
import com.shovel.filemanager.data.FileSelectionSummary
import com.shovel.filemanager.data.RecentItem
import com.shovel.filemanager.data.RecentFileItem
import com.shovel.filemanager.data.HomeCustomization
import com.shovel.filemanager.data.OptionalFeature
import com.shovel.filemanager.ui.components.featureVisible
import com.shovel.filemanager.data.HomeDisplayArea
import com.shovel.filemanager.data.HomeSection
import com.shovel.filemanager.data.HomeShortcut
import com.shovel.filemanager.data.SafLocation
import com.shovel.filemanager.advanced.AdvancedAccessBackend
import com.shovel.filemanager.ui.MainViewModel
import com.shovel.filemanager.ui.AppSection
import com.shovel.filemanager.ui.FileScrollKey
import com.shovel.filemanager.ui.PanelUiState
import com.shovel.filemanager.ui.ProgressiveScrollRules
import com.shovel.filemanager.ui.components.LocalFileVisual
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.components.ShovelPullToRefresh
import com.shovel.filemanager.ui.components.DirectoryGridItemContent
import com.shovel.filemanager.ui.components.DirectoryDisplayMenuItems
import com.shovel.filemanager.ui.components.DirectoryEntryFilter
import com.shovel.filemanager.ui.components.DirectoryEntryFilterDialog
import com.shovel.filemanager.ui.components.DirectoryEntryFilterRules
import com.shovel.filemanager.ui.components.DirectoryBrowserToolbar
import com.shovel.filemanager.ui.components.DirectoryLayoutButton
import com.shovel.filemanager.ui.components.DirectoryDisplaySettingsDialog
import com.shovel.filemanager.ui.components.DirectoryQuickSearchField
import com.shovel.filemanager.ui.components.DirectorySearchButton
import com.shovel.filemanager.ui.components.FileInfoDialog
import com.shovel.filemanager.ui.components.FileSizeBar
import com.shovel.filemanager.ui.components.longPressDragSelect
import com.shovel.filemanager.ui.components.LazyListFastScroller
import com.shovel.filemanager.ui.components.LazyGridFastScroller
import com.shovel.filemanager.ui.components.DeleteConfirmationDialog
import com.shovel.filemanager.ui.preview.PreviewSource
import com.shovel.filemanager.ui.components.SelectionActionDock
import com.shovel.filemanager.ui.components.SelectionHeader
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.UiTranslator
import com.shovel.filemanager.ui.localization.uiText
import com.shovel.filemanager.ui.localization.rememberLocalizedDateTimeFormat
import com.shovel.filemanager.ui.preview.openWith
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private data class ArchiveRequest(
    val sourcePaths: List<String>,
    val initialName: String,
) {
    companion object {
        fun fromSelection(paths: Collection<String>): ArchiveRequest? {
            val sources = paths.distinct()
            if (sources.isEmpty()) return null
            return ArchiveRequest(sources, defaultArchiveName(sources))
        }

        fun fromEntry(entry: FileEntry): ArchiveRequest =
            ArchiveRequest(listOf(entry.absolutePath), defaultArchiveName(listOf(entry.absolutePath)))
    }
}

private fun defaultArchiveName(paths: List<String>): String {
    if (paths.size != 1) return "archyvas"
    val fileName = File(paths.single()).name.ifBlank { return "archyvas" }
    val withoutArchiveSuffix = if (fileName.endsWith(".tar.gz", ignoreCase = true)) {
        fileName.dropLast(7)
    } else {
        fileName.substringBeforeLast('.', fileName)
    }
    return withoutArchiveSuffix.ifBlank { "archyvas" }
}

@Composable
fun FilesScreen(
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onAddSafLocation: () -> Unit,
    onOpenSystemFiles: () -> Unit,
) {
    val panelState by viewModel.panel.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val filesHomeDisplaySettings by viewModel.filesHomeDisplaySettings.collectAsStateWithLifecycle()
    val storageHomeDisplaySettings by viewModel.storageHomeDisplaySettings.collectAsStateWithLifecycle()
    val homeCustomization by viewModel.homeCustomization.collectAsStateWithLifecycle()
    val filesHomeVisible by viewModel.filesHomeVisible.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val renameUndo by viewModel.renameUndo.collectAsStateWithLifecycle()
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val advancedAccess by viewModel.advancedAccess.collectAsStateWithLifecycle()
    val fileCategory by viewModel.fileCategory.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    var showPermanentDelete by remember { mutableStateOf(false) }
    var archiveRequest by remember { mutableStateOf<ArchiveRequest?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var homeDisplayArea by remember { mutableStateOf<HomeDisplayArea?>(null) }
    var showHomeCustomization by remember { mutableStateOf(false) }
    var infoTargets by remember { mutableStateOf<List<FileEntry>?>(null) }
    var recentTrashEntries by remember { mutableStateOf<List<FileEntry>?>(null) }
    val clipboardAvailable = clipboard != null

    LaunchedEffect(clipboardAvailable) {
        if (!clipboardAvailable) showPaste = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (fileCategory.open) {
                    return@onPreviewKeyEvent when {
                        event.key == Key.Escape -> {
                            viewModel.closeFileCategory(); true
                        }
                        event.key == Key.F5 -> {
                            viewModel.refreshFileCategory(); true
                        }
                        event.isCtrlPressed && event.isShiftPressed && event.key == Key.C -> {
                            viewModel.copyFileCategorySelection(move = false, append = true); true
                        }
                        event.isCtrlPressed && event.key == Key.C -> {
                            viewModel.copyFileCategorySelection(move = false); true
                        }
                        event.isCtrlPressed && event.key == Key.X -> {
                            viewModel.copyFileCategorySelection(move = true); true
                        }
                        event.isCtrlPressed && event.key == Key.A -> {
                            viewModel.toggleAllFileCategoryEntries(fileCategory.entries.mapTo(linkedSetOf(), FileEntry::absolutePath)); true
                        }
                        else -> false
                    }
                }
                val state = panelState
                when {
                    event.isCtrlPressed && event.isShiftPressed && event.key == Key.T -> {
                        viewModel.restoreClosedTab(); true
                    }
                    event.isCtrlPressed && event.key == Key.T -> {
                        viewModel.newTab(); true
                    }
                    event.isCtrlPressed && event.key == Key.W -> {
                        viewModel.closeActiveTab(); true
                    }
                    event.isCtrlPressed && event.key == Key.C -> {
                        viewModel.copySelection(move = false); true
                    }
                    event.isCtrlPressed && event.key == Key.X -> {
                        viewModel.copySelection(move = true); true
                    }
                    event.isCtrlPressed && event.key == Key.V && clipboardAvailable -> {
                        showPaste = true; true
                    }
                    event.isCtrlPressed && event.key == Key.A -> {
                        viewModel.selectAll(); true
                    }
                    event.isAltPressed && event.key == Key.DirectionLeft -> viewModel.navigateBack()
                    event.isAltPressed && event.key == Key.DirectionRight -> viewModel.navigateForward()
                    event.key == Key.Backspace -> viewModel.navigateUp()
                    event.key == Key.F5 -> {
                        viewModel.refreshPanel(scrollToTop = true); true
                    }
                    event.key == Key.Delete && state.selectedPaths.isNotEmpty() -> {
                        showTrashConfirm = true; true
                    }
                    event.key == Key.F2 && state.selectedPaths.isNotEmpty() -> {
                        if (state.selectedPaths.size == 1) {
                            state.entries.firstOrNull { it.absolutePath in state.selectedPaths }?.let { renameTarget = it }
                        } else {
                            viewModel.beginBatchRename(state.entries.filter { it.absolutePath in state.selectedPaths }.map(FileEntry::absolutePath))
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!hasAllFilesAccess) {
                PermissionBanner(onRequestAllFilesAccess)
            }
            if (fileCategory.open) {
                PanelTabsBar(
                    workspace = tabs,
                    viewModel = viewModel,
                    onBeforeTabAction = viewModel::closeFileCategory,
                )
                FileCategoryBrowser(
                    state = fileCategory,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                )
            } else if (filesHomeVisible) {
                FilesHome(
                    roots = roots,
                    recentFiles = recentFiles.items,
                    recentAddedFiles = recentFiles.addedItems,
                    recentOpenedFiles = recentFiles.openedItems,
                    recentFilesLoading = recentFiles.loading,
                    recentFilesError = recentFiles.error,
                    storageDisplaySettings = storageHomeDisplaySettings,
                    quickLocationsDisplaySettings = filesHomeDisplaySettings,
                    customization = homeCustomization,
                    trashCount = trashItems.size,
                    rootStorageAvailable = advancedAccess.activeBackend in setOf(
                        AdvancedAccessBackend.ROOT,
                        AdvancedAccessBackend.SHIZUKU_ROOT,
                    ),
                    onOpen = { location -> viewModel.openHomeShortcut(location.id, location.path) },
                    onOpenStorage = { root -> viewModel.openStorageRoot(root) },
                    onOpenRoot = { viewModel.openRootFromHome() },
                    onOpenRecent = { entry -> viewModel.open(entry) },
                    onRenameRecent = { entry -> renameTarget = entry },
                    onShareRecent = { entries -> viewModel.shareEntries(entries.map(FileEntry::absolutePath)) },
                    onTrashRecent = { entries -> recentTrashEntries = entries },
                    onRevealRecent = { entry -> viewModel.revealLocalEntry(entry.absolutePath) },
                    onCopyRecent = { entries, move -> viewModel.copyEntries(entries.map(FileEntry::absolutePath), move) },
                    onOpenTrash = viewModel::openTrashFromHome,
                    onOpenCleanup = viewModel::openCleanupFromHome,
                    onRefreshRecent = viewModel::refreshRecentFiles,
                    onToggleLayout = viewModel::toggleHomeDisplayLayout,
                    onConfigureLayout = { area -> homeDisplayArea = area },
                    onOpenSystemFiles = onOpenSystemFiles,
                    onCustomizeHome = { showHomeCustomization = true },
                )
            } else {
                FilePanel(
                    state = panelState,
                    tabs = tabs,
                    clipboardAvailable = clipboardAvailable,
                    batchRenameUndoAvailable = renameUndo != null,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    onCreate = { showCreate = true },
                    onRename = { renameTarget = it },
                    onTrash = { showTrashConfirm = true },
                    onPermanentDelete = { showPermanentDelete = true },
                    onTrashEntry = { entry -> viewModel.selectOnly(entry.absolutePath); showTrashConfirm = true },
                    onInfo = { infoTargets = it },
                    onArchive = {
                        archiveRequest = ArchiveRequest.fromSelection(panelState.selectedPaths)
                    },
                    onArchiveEntry = { entry -> archiveRequest = ArchiveRequest.fromEntry(entry) },
                    onPaste = { showPaste = true },
                    onUndoBatchRename = viewModel::undoBatchRename,
                    onDisplaySettings = { showDisplaySettings = true },
                )
            }
        }
    }

    if (showCreate) {
        CreateItemDialog(
            onDismiss = { showCreate = false },
            onCreateFolder = { name -> viewModel.createDirectory(name); showCreate = false },
            onCreateFile = { name -> viewModel.createFile(name); showCreate = false },
            onCreateArchive = { name, format ->
                viewModel.createArchive(name, format, sourcePaths = emptyList())
                showCreate = false
            },
        )
    }
    renameTarget?.let { entry ->
        TextInputDialog(
            title = "Pervadinti",
            initial = entry.name,
            confirmLabel = "Pervadinti",
            onDismiss = { renameTarget = null },
            onConfirm = { name -> viewModel.rename(entry.absolutePath, name); renameTarget = null },
        )
    }
    if (showTrashConfirm) {
        val selectedState = panelState
        val paths = selectedState.selectedPaths.toList()
        val entries = selectedState.entries.filter { it.absolutePath in selectedState.selectedPaths }
        DeleteConfirmationDialog(
            names = entries.map(FileEntry::name).ifEmpty { paths.map { java.io.File(it).name } },
            permanent = false,
            onDismiss = { showTrashConfirm = false },
            onConfirm = { viewModel.moveSelectionToTrash(); showTrashConfirm = false },
            loadSummary = { viewModel.loadFileSelectionInfo(paths) },
        )
    }
    recentTrashEntries?.let { entries ->
        val paths = entries.map(FileEntry::absolutePath)
        DeleteConfirmationDialog(
            names = entries.map(FileEntry::name),
            permanent = false,
            onDismiss = { recentTrashEntries = null },
            onConfirm = {
                viewModel.moveLocalPathsToTrash(paths)
                recentTrashEntries = null
            },
            loadSummary = { viewModel.loadFileSelectionInfo(paths) },
        )
    }
    if (showPermanentDelete) {
        val selectedState = panelState
        val paths = selectedState.selectedPaths.toList()
        val entries = selectedState.entries.filter { it.absolutePath in selectedState.selectedPaths }
        DeleteConfirmationDialog(
            names = entries.map(FileEntry::name).ifEmpty { paths.map { java.io.File(it).name } },
            permanent = true,
            onDismiss = { showPermanentDelete = false },
            onConfirm = { viewModel.deleteSelectionPermanently(); showPermanentDelete = false },
            loadSummary = { viewModel.loadFileSelectionInfo(paths) },
        )
    }
    archiveRequest?.let { request ->
        ArchiveDialog(
            initialName = request.initialName,
            sourcePaths = request.sourcePaths,
            loadSummary = viewModel::loadFileSelectionInfo,
            onDismiss = { archiveRequest = null },
            onCreate = { name, format, password, compressionLevel, maxOutputBytes, hideNames ->
                viewModel.createArchive(
                    name,
                    format,
                    password,
                    request.sourcePaths,
                    compressionLevel,
                    maxOutputBytes,
                    hideNames,
                )
                archiveRequest = null
            },
        )
    }
    if (showPaste) {
        if (clipboard != null) {
            TransferOptionsDialog(
                moving = clipboard?.mode == ClipboardMode.MOVE,
                onDismiss = { showPaste = false },
                onConfirm = { policy, verification, failurePolicy ->
                    viewModel.paste(policy, verification, failurePolicy)
                    showPaste = false
                },
            )
        }
    }
    if (showDisplaySettings) {
        val state = panelState
        DirectoryDisplaySettingsDialog(
            initialSettings = state.toDirectoryDisplaySettings(),
            thumbnailsAvailable = true,
            initialSortMode = state.sortMode,
            initialSortDirection = state.sortDirection,
            onDismiss = { showDisplaySettings = false },
            onApply = { settings ->
                viewModel.setDirectoryDisplaySettings(settings)
                showDisplaySettings = false
            },
            onApplySort = { mode, direction -> viewModel.setSort(mode, direction) },
            onApplyToAll = { settings, mode, direction ->
                viewModel.applyDirectoryDisplaySettingsToAll(settings, mode, direction)
                showDisplaySettings = false
            },
            extraContent = {
                HorizontalDivider()
                val folderDetails by viewModel.folderDetailsEnabled.collectAsStateWithLifecycle()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        LText("Folder details", fontWeight = FontWeight.SemiBold)
                        LText(
                            "Shows how many items are inside each folder and archive and how much space they use. " +
                                "Hidden items follow the Show hidden setting. Applies to all folders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = folderDetails,
                        onCheckedChange = viewModel::setFolderDetailsEnabled,
                        modifier = Modifier.testTag("display_folder_details"),
                    )
                }
            },
        )
    }
    homeDisplayArea?.let { area ->
        val settings = when (area) {
            HomeDisplayArea.STORAGE -> storageHomeDisplaySettings
            HomeDisplayArea.QUICK_LOCATIONS -> filesHomeDisplaySettings
        }
        DirectoryDisplaySettingsDialog(
            initialSettings = settings,
            thumbnailsAvailable = false,
            gridColumnRange = if (area == HomeDisplayArea.STORAGE) 1..3 else 1..6,
            onDismiss = { homeDisplayArea = null },
            onApply = { settings ->
                viewModel.setHomeDisplaySettings(area, settings)
                homeDisplayArea = null
            },
            extraContent = {
                if (area == HomeDisplayArea.STORAGE || area == HomeDisplayArea.QUICK_LOCATIONS) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            homeDisplayArea = null
                            showHomeCustomization = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("home_customize"),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        LText(
                            if (area == HomeDisplayArea.STORAGE) "Tvarkyti saugyklas ir pradžios ekraną"
                            else "Tvarkyti greitas vietas",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
        )
    }
    infoTargets?.let { entries ->
        FileInfoDialog(
            entries = entries,
            loadSummary = viewModel::loadFileSelectionInfo,
            onDismiss = { infoTargets = null },
            loadFolderStats = { entry -> viewModel.folderStats(entry, panelState.includeHidden) },
        )
    }
    if (showHomeCustomization) {
        val currentPath = panelState.path
        HomeCustomizationDialog(
            customization = homeCustomization,
            roots = roots,
            currentPath = currentPath,
            onDismiss = { showHomeCustomization = false },
            onMoveSection = viewModel::moveHomeSection,
            onMoveShortcut = viewModel::moveHomeShortcut,
            onSetShortcutVisible = viewModel::setHomeShortcutVisible,
            onSetShortcutSection = viewModel::setHomeShortcutSection,
            onSetSectionVisible = viewModel::setHomeSectionVisible,
            onMoveTool = viewModel::moveHomeTool,
            onSetToolVisible = viewModel::setHomeToolVisible,
            onMoveStorage = viewModel::moveHomeStorage,
            onSetStorageVisible = viewModel::setHomeStorageVisible,
            onRemoveShortcut = viewModel::removeHomeShortcut,
            onAddShortcut = viewModel::addHomeShortcut,
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Storage, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                LText("Reikia prieigos prie bendrų failų", fontWeight = FontWeight.SemiBold)
                LText(
                    "Be jos programa matys tik sistemos leistas vietas. Android/data vis tiek lieka sistemos ribojamas.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onRequest) { LText("Suteikti") }
        }
    }
}

private fun itemCountLabel(count: Int): String = if (count == 1) "1 elementas" else "$count elementų"

private fun PanelUiState.toDirectoryDisplaySettings(): DirectoryDisplaySettings = DirectoryDisplaySettings(
    layoutMode = if (grid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
    iconScalePercent = iconScalePercent,
    spacingScalePercent = spacingScalePercent,
    gridColumns = gridColumns,
    gridStyle = gridStyle,
    showThumbnails = showThumbnails,
)

@Composable
private fun FilePanel(
    state: PanelUiState,
    tabs: PanelWorkspace,
    clipboardAvailable: Boolean,
    batchRenameUndoAvailable: Boolean,
    modifier: Modifier,
    viewModel: MainViewModel,
    onCreate: () -> Unit,
    onRename: (FileEntry) -> Unit,
    onTrash: () -> Unit,
    onPermanentDelete: () -> Unit,
    onTrashEntry: (FileEntry) -> Unit,
    onInfo: (List<FileEntry>) -> Unit,
    onArchive: () -> Unit,
    onArchiveEntry: (FileEntry) -> Unit,
    onPaste: () -> Unit,
    onUndoBatchRename: () -> Unit,
    onDisplaySettings: () -> Unit,
) {
    var compactMenu by remember { mutableStateOf(false) }
    var showViewingHistory by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var entryFilters by remember(tabs.activeTabId, state.path) { mutableStateOf<Set<DirectoryEntryFilter>>(emptySet()) }
    var showEntryFilter by remember(tabs.activeTabId, state.path) { mutableStateOf(false) }
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val localClipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    LaunchedEffect(state.path) {
        searchVisible = false
        searchQuery = ""
    }
    val displayedEntries = remember(state.entries, searchQuery, entryFilters) {
        DirectoryEntryFilterRules.visibleEntries(state.entries, searchQuery, entryFilters)
    }
    val displayedState = state.copy(entries = displayedEntries)
    val allEntriesSelected = displayedEntries.isNotEmpty() && displayedEntries.all { it.absolutePath in state.selectedPaths }
    val scrollKey = remember(tabs.activeTabId, state.path, state.grid) {
        FileScrollKey(tabId = tabs.activeTabId, path = state.path, grid = state.grid)
    }
    Column(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        PanelTabsBar(tabs, viewModel)
        if (state.selectedPaths.isNotEmpty()) {
            SelectionHeader(
                count = state.selectedPaths.size,
                allSelected = allEntriesSelected,
                onClose = { viewModel.clearSelection() },
                onToggleSelectAll = {
                    if (allEntriesSelected) viewModel.clearSelection()
                    else viewModel.selectPaths(displayedEntries.map(FileEntry::absolutePath))
                },
            )
        } else {
            DirectoryBrowserToolbar(
                title = File(state.path).name.ifBlank { state.path },
                path = state.path,
                backEnabled = state.backHistory.isNotEmpty(),
                forwardEnabled = state.forwardHistory.isNotEmpty(),
                upEnabled = File(state.path).parentFile != null,
                searchActive = searchVisible,
                grid = state.grid,
                testTagPrefix = "local",
                onBack = { viewModel.navigateBack() },
                onForward = { viewModel.navigateForward() },
                onUp = { viewModel.navigateUp() },
                onToggleSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                },
                onToggleLayout = { viewModel.toggleGrid() },
                onOpenSettings = onDisplaySettings,
            ) {
                CompactPanelActions(
                    expanded = compactMenu,
                    onExpandedChange = { compactMenu = it },
                    state = state,
                    clipboardAvailable = clipboardAvailable,
                    batchRenameUndoAvailable = batchRenameUndoAvailable,
                    viewModel = viewModel,
                    onPaste = onPaste,
                    onUndoBatchRename = onUndoBatchRename,
                    onDisplaySettings = onDisplaySettings,
                    onShowViewingHistory = { showViewingHistory = true },
                    filterActive = entryFilters.isNotEmpty(),
                    onOpenFilter = { showEntryFilter = true },
                )
            }
        }

        if (searchVisible) {
            DirectoryQuickSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = { searchVisible = false; searchQuery = "" },
                filterActive = entryFilters.isNotEmpty(),
                onOpenFilter = { showEntryFilter = true },
                modifier = Modifier.testTag("directory_search_field_local"),
            )
        }

        Breadcrumbs(state.path) { path -> viewModel.navigate(path) }
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LText(
                "Rasta ${state.entries.size} · metaduomenys ${state.listingMetadataEntries}/${state.entries.size}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.listingTruncated) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                LText(
                    "Rodomi pirmi ${state.entries.size} elementų. Sąrašas sutrumpintas, failai nepakeisti.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (state.path == "/") {
                    LText(
                        "Įjunkite Root arba Shizuku root prieigą skiltyje Daugiau",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ShovelPullToRefresh(
            isRefreshing = state.loading,
            onRefresh = { viewModel.refreshPanel(scrollToTop = true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            testTag = "pull_to_refresh_local",
        ) {
            when {
                state.error != null -> EmptyPanel("Aplankas nepasiekiamas", state.error)
                state.loading && state.entries.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.entries.isEmpty() -> EmptyPanel("Aplankas tuščias", "Čia dar nėra failų")
                displayedEntries.isEmpty() -> EmptyPanel("Atitikmenų nerasta", "Pabandykite kitą pavadinimą")
                state.grid -> FileGrid(displayedState, scrollKey, viewModel, onRename, { onInfo(listOf(it)) }, onTrashEntry, onArchiveEntry)
                else -> FileList(displayedState, scrollKey, viewModel, onRename, { onInfo(listOf(it)) }, onTrashEntry, onArchiveEntry)
            }
            if (state.selectedPaths.isNotEmpty()) {
                val bookmarkedPaths = viewModel.homeCustomization.collectAsStateWithLifecycle().value.shortcuts.mapTo(hashSetOf()) { it.path }
                val selectionBookmarked = state.selectedPaths.all { it in bookmarkedPaths }
                SelectionActionDock(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    IconButton(onClick = { viewModel.copySelection(move = false) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = uiText("Kopijuoti"))
                    }
                    IconButton(
                        onClick = { viewModel.bookmarkSelection() },
                        modifier = Modifier.testTag("selection_bookmark_local"),
                    ) {
                        Icon(
                            if (selectionBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                            contentDescription = uiText(if (selectionBookmarked) "Pašalinti žymelę" else "Pridėti žymelę"),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.shareSelection() },
                        modifier = Modifier.testTag("selection_share_local"),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = uiText("Bendrinti"))
                    }
                    IconButton(
                        onClick = {
                            onInfo(state.entries.filter { it.absolutePath in state.selectedPaths })
                        },
                        modifier = Modifier.testTag("selection_info_local"),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = uiText("Informacija"))
                    }
                    IconButton(onClick = { viewModel.copySelection(move = true) }) {
                        Icon(Icons.Rounded.ContentCut, contentDescription = uiText("Perkelti"))
                    }
                    if (state.selectedPaths.size == 1 || featureVisible(OptionalFeature.BATCH_RENAME)) IconButton(onClick = {
                        val selectedEntries = state.entries.filter { it.absolutePath in state.selectedPaths }
                        if (selectedEntries.size == 1) selectedEntries.firstOrNull()?.let(onRename)
                        else viewModel.beginBatchRename(selectedEntries.map(FileEntry::absolutePath))
                    }) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = uiText(if (state.selectedPaths.size == 1) "Pervadinti" else "Masinis pervadinimas"),
                        )
                    }
                    IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, contentDescription = uiText("Archyvuoti")) }
                    val selectedEntries = state.entries.filter { it.absolutePath in state.selectedPaths }
                    if (featureVisible(OptionalFeature.ANALYSIS)) IconButton(
                        onClick = { viewModel.analyzeEntries(selectedEntries.map(FileEntry::absolutePath)) },
                        enabled = selectedEntries.isNotEmpty(),
                        modifier = Modifier.testTag("analyze_selection_local"),
                    ) {
                        Icon(Icons.Rounded.Analytics, contentDescription = uiText("Analizuoti pasirinktus"))
                    }
                    IconButton(onClick = onTrash) {
                        Icon(Icons.Rounded.Delete, contentDescription = uiText("Į šiukšlinę"), tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onPermanentDelete, modifier = Modifier.testTag("delete_permanently_local")) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = uiText("Ištrinti visam laikui"),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { onCreate() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("create_local_item"),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = uiText("Sukurti"))
                }
            }
        }
    }
    if (showViewingHistory) {
        ViewingHistoryDialog(
            recents = recents,
            onDismiss = { showViewingHistory = false },
            onOpen = { recent ->
                showViewingHistory = false
                viewModel.openQuickPath(recent.path)
            },
            onClear = viewModel::clearRecents,
        )
    }
    if (showEntryFilter) {
        DirectoryEntryFilterDialog(
            selected = entryFilters,
            onSelectedChange = { entryFilters = it },
            onDismiss = { showEntryFilter = false },
        )
    }
}

@Composable
private fun CompactPanelActions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: PanelUiState,
    clipboardAvailable: Boolean,
    batchRenameUndoAvailable: Boolean,
    viewModel: MainViewModel,
    onPaste: () -> Unit,
    onUndoBatchRename: () -> Unit,
    onDisplaySettings: () -> Unit,
    onShowViewingHistory: () -> Unit,
    filterActive: Boolean,
    onOpenFilter: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = uiText("Aplanko veiksmai"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            if (clipboardAvailable) {
                DropdownMenuItem(
                    text = { LText("Įklijuoti") },
                    leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                    onClick = { onExpandedChange(false); onPaste() },
                )
            }
            if (batchRenameUndoAvailable) {
                DropdownMenuItem(
                    text = { LText("Atšaukti paskutinį masinį pervadinimą") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                    onClick = { onExpandedChange(false); onUndoBatchRename() },
                )
            }
            if (featureVisible(OptionalFeature.RECENT)) DropdownMenuItem(
                text = { LText("Peržiūrų istorija") },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                onClick = { onExpandedChange(false); onShowViewingHistory() },
            )
            DropdownMenuItem(
                text = { LText("Operacijų centras") },
                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                modifier = Modifier.testTag("open_operations"),
                onClick = {
                    onExpandedChange(false)
                    viewModel.setSection(AppSection.OPERATIONS)
                },
            )
            HorizontalDivider()
            DirectoryDisplayMenuItems(
                grid = state.grid,
                includeHidden = state.includeHidden,
                showThumbnails = state.showThumbnails,
                thumbnailsAvailable = true,
                sortMode = state.sortMode,
                sortDirection = state.sortDirection,
                displaySettingsTestTag = "display_settings",
                onToggleHidden = { viewModel.toggleHidden() },
                onToggleLayout = { viewModel.toggleGrid() },
                onToggleThumbnails = { viewModel.toggleThumbnails() },
                onOpenSettings = onDisplaySettings,
                onSort = { viewModel.setSort(it) },
                onDismissMenu = { onExpandedChange(false) },
                filterActive = filterActive,
                onOpenFilter = onOpenFilter,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { LText("Atnaujinti") },
                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                onClick = { viewModel.refreshPanel(scrollToTop = true); onExpandedChange(false) },
            )
        }
    }
}

@Composable
private fun ViewingHistoryDialog(
    recents: List<RecentItem>,
    onDismiss: () -> Unit,
    onOpen: (RecentItem) -> Unit,
    onClear: () -> Unit,
) {
    val dateFormat = rememberLocalizedDateTimeFormat(DateFormat.SHORT, DateFormat.SHORT)
    ShovelModalDialog(
        title = "Peržiūrų istorija",
        icon = Icons.Rounded.History,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("viewing_history_dialog"),
        showFooter = recents.isNotEmpty(),
        expandedContent = true,
        actions = {
            if (recents.isNotEmpty()) TextButton(onClick = onClear) { LText("Išvalyti istoriją") }
        },
    ) {
        if (recents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                LText("Peržiūrėtų failų istorija tuščia")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                items(recents, key = { "history:${it.path}" }) { recent ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(recent) }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(File(recent.path).name.ifBlank { recent.path }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(recent.path, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dateFormat.format(Date(recent.openedAtMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PanelTabsBar(
    workspace: PanelWorkspace,
    viewModel: MainViewModel,
    onBeforeTabAction: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    com.shovel.filemanager.ui.theme.AppearanceContentOn(MaterialTheme.colorScheme.surfaceContainerLow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_tabs")
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(22.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        workspace.tabs.forEach { tab ->
            FilterChip(
                selected = tab.id == workspace.activeTabId,
                onClick = { onBeforeTabAction(); viewModel.activateTab(tab.id) },
                label = { Text(File(tab.path).name.ifBlank { tab.path }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (tab.locked) {
                    { Icon(Icons.Rounded.Lock, contentDescription = uiText("Užrakinta"), modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }
        IconButton(onClick = { onBeforeTabAction(); viewModel.newTab() }) {
            Icon(Icons.Rounded.Add, contentDescription = uiText("Nauja kortelė"))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = uiText("Kortelės veiksmai")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { LText("Dubliuoti kortelę") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = { onBeforeTabAction(); viewModel.duplicateTab(); menu = false },
                )
                DropdownMenuItem(
                    text = { LText(if (workspace.activeTab.locked) "Atrakinti kortelę" else "Užrakinti kortelę") },
                    leadingIcon = {
                        Icon(if (workspace.activeTab.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, contentDescription = null)
                    },
                    onClick = { onBeforeTabAction(); viewModel.toggleTabLock(); menu = false },
                )
                DropdownMenuItem(
                    text = { LText("Uždaryti kortelę") },
                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    enabled = workspace.tabs.size > 1 && !workspace.activeTab.locked,
                    onClick = { onBeforeTabAction(); viewModel.closeActiveTab(); menu = false },
                )
                DropdownMenuItem(
                    text = { LText("Atkurti uždarytą kortelę") },
                    leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
                    enabled = workspace.closedTabs.isNotEmpty(),
                    onClick = { onBeforeTabAction(); viewModel.restoreClosedTab(); menu = false },
                )
            }
        }
    }
}

}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val file = remember(path) { runCatching { File(path).canonicalFile }.getOrElse { File(path).absoluteFile } }
    val chain = remember(path) {
        generateSequence(file) { it.parentFile }.toList().asReversed()
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        chain.forEach { part ->
            AssistChip(
                onClick = { onNavigate(part.absolutePath) },
                label = { Text(part.name.ifBlank { part.absolutePath }, maxLines = 1) },
            )
        }
    }
}

@Composable
internal fun FileList(
    state: PanelUiState,
    scrollKey: FileScrollKey,
    viewModel: MainViewModel,
    onRename: (FileEntry) -> Unit,
    onInfo: (FileEntry) -> Unit,
    onTrash: (FileEntry) -> Unit,
    onArchive: (FileEntry) -> Unit,
) {
    val context = LocalContext.current
    val bookmarkedPaths = viewModel.homeCustomization.collectAsStateWithLifecycle().value.shortcuts.mapTo(hashSetOf()) { it.path }
    val folderDetailsEnabled by viewModel.folderDetailsEnabled.collectAsStateWithLifecycle()
    val interfaceLanguage = LocalConfiguration.current.locales[0].language
    val chooserError = uiText("Programų pasirinkiklio atidaryti nepavyko")
    val dateFormat = rememberLocalizedDateTimeFormat(DateFormat.SHORT, DateFormat.SHORT)
    val largestSizeBytes = remember(state.entries, state.sortMode) {
        state.entries.takeIf { state.sortMode == SortMode.SIZE }
            ?.asSequence()
            ?.filter(FileEntry::metadataComplete)
            ?.maxOfOrNull(FileEntry::sizeBytes)
    }
    val initialPosition = remember(scrollKey) { viewModel.fileScrollPosition(scrollKey) }
    var restoringPosition by remember(scrollKey) {
        mutableStateOf(initialPosition.firstVisibleItemIndex > 0 || initialPosition.firstVisibleItemScrollOffset > 0)
    }

    var pinInitialTop by remember(scrollKey) {
        mutableStateOf(ProgressiveScrollRules.startsPinnedToTop(initialPosition))
    }
    val listState = key(scrollKey) {
        rememberLazyListState()
    }
    var handledTopRequest by remember(scrollKey) { mutableStateOf(state.scrollToTopRequest) }
    LaunchedEffect(scrollKey, state.scrollToTopRequest) {
        if (state.scrollToTopRequest != handledTopRequest) {
            handledTopRequest = state.scrollToTopRequest
            restoringPosition = false
            pinInitialTop = true
            listState.requestScrollToItem(0)
        }
    }
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(scrollKey, userDragging) {
        if (userDragging) pinInitialTop = false
    }
    LaunchedEffect(scrollKey, state.entries.size, state.entries.firstOrNull()?.absolutePath, state.loading, pinInitialTop) {
        if (pinInitialTop && state.entries.isNotEmpty()) {
            // Apply at the next measure so stable item keys cannot anchor an
            // early partial result (such as Android) above the final first item.
            listState.requestScrollToItem(0)
            if (!state.loading) pinInitialTop = false
        } else if (restoringPosition && state.entries.isNotEmpty() &&
            (state.entries.size > initialPosition.firstVisibleItemIndex || !state.loading)
        ) {
            val index = initialPosition.firstVisibleItemIndex.coerceAtMost(state.entries.lastIndex)
            val offset = initialPosition.firstVisibleItemScrollOffset.takeIf {
                index == initialPosition.firstVisibleItemIndex
            } ?: 0
            listState.scrollToItem(index, offset)
            restoringPosition = false
        }
    }
    LaunchedEffect(scrollKey, listState) {
        snapshotFlow {
            val position = ProgressiveScrollRules.positionToPersist(
                pinnedToTop = pinInitialTop,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
            Triple(restoringPosition, position.firstVisibleItemIndex, position.firstVisibleItemScrollOffset)
        }.collect { (restoring, index, offset) ->
            if (!restoring) viewModel.saveFileScrollPosition(scrollKey, index, offset)
        }
    }
    DisposableEffect(scrollKey, listState, pinInitialTop) {
        onDispose {
            if (!restoringPosition) {
                val position = ProgressiveScrollRules.positionToPersist(
                    pinnedToTop = pinInitialTop,
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                )
                viewModel.saveFileScrollPosition(
                    scrollKey,
                    position.firstVisibleItemIndex,
                    position.firstVisibleItemScrollOffset,
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .longPressDragSelect(
                    state = listState,
                    itemCount = state.entries.size,
                    isSelected = { index -> state.entries.getOrNull(index)?.absolutePath in state.selectedPaths },
                    onSelectionChange = { indices, selected ->
                        viewModel.setSelection(
                            indices.mapNotNull { index -> state.entries.getOrNull(index)?.absolutePath },
                            selected,
                        )
                    },
                )
                .testTag("file_list"),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(state.entries, key = FileEntry::absolutePath, contentType = FileEntry::kind) { entry ->
                val details = rememberFolderDetails(viewModel, entry, state.includeHidden, folderDetailsEnabled) { listState.isScrollInProgress }
                val metadata = remember(entry, state.sortMode, details) {
                    entryMeta(
                        entry,
                        dateFormat,
                        showDirectorySize = state.sortMode == SortMode.SIZE,
                        details = folderDetailsText(entry, details),
                    )
                }
                FileRow(
                    entry = entry,
                    metadata = metadata,
                    selected = entry.absolutePath in state.selectedPaths,
                    showThumbnails = state.showThumbnails,
                    iconScalePercent = state.iconScalePercent,
                    spacingScalePercent = state.spacingScalePercent,
                    largestSizeBytes = largestSizeBytes,
                    onClick = { handleEntryClick(state, entry, viewModel) },
                    onLongClick = { viewModel.toggleSelection(entry.absolutePath) },
                    onPreview = {viewModel.open(entry) },
                    onOpenWith = {
                        runCatching { openWith(context, PreviewSource.Local(entry)) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message?.let { message -> UiTranslator.translate(message, interfaceLanguage) } ?: chooserError,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    },
                    onSelect = { viewModel.toggleSelection(entry.absolutePath) },
                    onRename = { onRename(entry) },
                    onInfo = { onInfo(entry) },
                    onTrash = { onTrash(entry) },
                    onArchive = { onArchive(entry) },
                    onAnalyze = { viewModel.analyzeEntries(listOf(entry.absolutePath)) },
                    onCopy = { viewModel.copyEntry(entry.absolutePath, move = false) },
                    onMove = { viewModel.copyEntry(entry.absolutePath, move = true) },
                    onBookmark = { viewModel.bookmarkPath(entry.absolutePath) },
                    bookmarked = entry.absolutePath in bookmarkedPaths,
                    onShare = { viewModel.shareEntry(entry.absolutePath) },
                )
            }
        }
        if (state.entries.isNotEmpty()) Spacer(Modifier.size(1.dp).testTag("file_list_content"))
        LazyListFastScroller(listState)
        if (!state.loading) Spacer(Modifier.size(1.dp).testTag("file_list_ready"))
    }
}

@Composable
internal fun FileGrid(
    state: PanelUiState,
    scrollKey: FileScrollKey,
    viewModel: MainViewModel,
    onRename: (FileEntry) -> Unit,
    onInfo: (FileEntry) -> Unit,
    onTrash: (FileEntry) -> Unit,
    onArchive: (FileEntry) -> Unit,
) {
    val context = LocalContext.current
    val bookmarkedPaths = viewModel.homeCustomization.collectAsStateWithLifecycle().value.shortcuts.mapTo(hashSetOf()) { it.path }
    val folderDetailsEnabled by viewModel.folderDetailsEnabled.collectAsStateWithLifecycle()
    val interfaceLanguage = LocalConfiguration.current.locales[0].language
    val chooserError = uiText("Programų pasirinkiklio atidaryti nepavyko")
    val largestSizeBytes = remember(state.entries, state.sortMode) {
        state.entries.takeIf { state.sortMode == SortMode.SIZE }
            ?.asSequence()
            ?.filter(FileEntry::metadataComplete)
            ?.maxOfOrNull(FileEntry::sizeBytes)
    }
    val initialPosition = remember(scrollKey) { viewModel.fileScrollPosition(scrollKey) }
    var restoringPosition by remember(scrollKey) {
        mutableStateOf(initialPosition.firstVisibleItemIndex > 0 || initialPosition.firstVisibleItemScrollOffset > 0)
    }
    var pinInitialTop by remember(scrollKey) {
        mutableStateOf(ProgressiveScrollRules.startsPinnedToTop(initialPosition))
    }
    val gridState = key(scrollKey) {
        rememberLazyGridState()
    }
    var handledTopRequest by remember(scrollKey) { mutableStateOf(state.scrollToTopRequest) }
    LaunchedEffect(scrollKey, state.scrollToTopRequest) {
        if (state.scrollToTopRequest != handledTopRequest) {
            handledTopRequest = state.scrollToTopRequest
            restoringPosition = false
            pinInitialTop = true
            gridState.requestScrollToItem(0)
        }
    }
    val userDragging by gridState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(scrollKey, userDragging) {
        if (userDragging) pinInitialTop = false
    }
    LaunchedEffect(scrollKey, state.entries.size, state.entries.firstOrNull()?.absolutePath, state.loading, pinInitialTop) {
        if (pinInitialTop && state.entries.isNotEmpty()) {
            // Apply at the next measure so stable item keys cannot anchor an
            // early partial result (such as Android) above the final first item.
            gridState.requestScrollToItem(0)
            if (!state.loading) pinInitialTop = false
        } else if (restoringPosition && state.entries.isNotEmpty() &&
            (state.entries.size > initialPosition.firstVisibleItemIndex || !state.loading)
        ) {
            val index = initialPosition.firstVisibleItemIndex.coerceAtMost(state.entries.lastIndex)
            val offset = initialPosition.firstVisibleItemScrollOffset.takeIf {
                index == initialPosition.firstVisibleItemIndex
            } ?: 0
            gridState.scrollToItem(index, offset)
            restoringPosition = false
        }
    }
    LaunchedEffect(scrollKey, gridState) {
        snapshotFlow {
            val position = ProgressiveScrollRules.positionToPersist(
                pinnedToTop = pinInitialTop,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
            Triple(restoringPosition, position.firstVisibleItemIndex, position.firstVisibleItemScrollOffset)
        }.collect { (restoring, index, offset) ->
            if (!restoring) viewModel.saveFileScrollPosition(scrollKey, index, offset)
        }
    }
    DisposableEffect(scrollKey, gridState, pinInitialTop) {
        onDispose {
            if (!restoringPosition) {
                val position = ProgressiveScrollRules.positionToPersist(
                    pinnedToTop = pinInitialTop,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                )
                viewModel.saveFileScrollPosition(
                    scrollKey,
                    position.firstVisibleItemIndex,
                    position.firstVisibleItemScrollOffset,
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(state.gridColumns.coerceIn(1, 6)),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .longPressDragSelect(
                    state = gridState,
                    itemCount = state.entries.size,
                    isSelected = { index -> state.entries.getOrNull(index)?.absolutePath in state.selectedPaths },
                    onSelectionChange = { indices, selected ->
                        viewModel.setSelection(
                            indices.mapNotNull { index -> state.entries.getOrNull(index)?.absolutePath },
                            selected,
                        )
                    },
                )
                .testTag("file_grid"),
            contentPadding = PaddingValues(8.dp, 6.dp, 8.dp, 88.dp),
            horizontalArrangement = Arrangement.spacedBy((5f * state.spacingScalePercent / 100f).dp),
            verticalArrangement = Arrangement.spacedBy((5f * state.spacingScalePercent / 100f).dp),
        ) {
            items(state.entries, key = FileEntry::absolutePath, contentType = FileEntry::kind) { entry ->
                val details = rememberFolderDetails(viewModel, entry, state.includeHidden, folderDetailsEnabled) { gridState.isScrollInProgress }
                FileTile(
                    entry = entry,
                    detailText = folderDetailsText(entry, details),
                    selected = entry.absolutePath in state.selectedPaths,
                    showThumbnails = state.showThumbnails,
                    iconScalePercent = state.iconScalePercent,
                    spacingScalePercent = state.spacingScalePercent,
                    gridStyle = state.gridStyle,
                    largestSizeBytes = largestSizeBytes,
                    onClick = { handleEntryClick(state, entry, viewModel) },
                    onLongClick = { viewModel.toggleSelection(entry.absolutePath) },
                    onPreview = {viewModel.open(entry) },
                    onOpenWith = {
                        runCatching { openWith(context, PreviewSource.Local(entry)) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message?.let { message -> UiTranslator.translate(message, interfaceLanguage) } ?: chooserError,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    },
                    onSelect = { viewModel.toggleSelection(entry.absolutePath) },
                    onRename = { onRename(entry) },
                    onInfo = { onInfo(entry) },
                    onTrash = { onTrash(entry) },
                    onArchive = { onArchive(entry) },
                    onAnalyze = { viewModel.analyzeEntries(listOf(entry.absolutePath)) },
                    onCopy = { viewModel.copyEntry(entry.absolutePath, move = false) },
                    onMove = { viewModel.copyEntry(entry.absolutePath, move = true) },
                    onBookmark = { viewModel.bookmarkPath(entry.absolutePath) },
                    bookmarked = entry.absolutePath in bookmarkedPaths,
                    onShare = { viewModel.shareEntry(entry.absolutePath) },
                )
            }
        }
        if (state.entries.isNotEmpty()) Spacer(Modifier.size(1.dp).testTag("file_grid_content"))
        LazyGridFastScroller(gridState)
        if (!state.loading) Spacer(Modifier.size(1.dp).testTag("file_grid_ready"))
    }
}

private fun handleEntryClick(state: PanelUiState, entry: FileEntry, viewModel: MainViewModel) {
    if (state.selectedPaths.isNotEmpty()) viewModel.toggleSelection(entry.absolutePath) else viewModel.open(entry)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    metadata: String,
    selected: Boolean,
    showThumbnails: Boolean,
    iconScalePercent: Int,
    spacingScalePercent: Int,
    largestSizeBytes: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreview: () -> Unit,
    onOpenWith: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onTrash: () -> Unit,
    onArchive: () -> Unit,
    onAnalyze: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onBookmark: () -> Unit,
    bookmarked: Boolean,
    onShare: () -> Unit,
) {
    val selectionShape = RoundedCornerShape(8.dp)
    val iconSize = (42f * iconScalePercent / 100f).dp
    val verticalPadding = (9f * spacingScalePercent / 100f).dp
    val itemSpacing = (12f * spacingScalePercent / 100f).dp
    val itemAlpha = if (entry.isHidden && !selected) 0.64f else 1f
    com.shovel.filemanager.ui.theme.AppearanceContentOn(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_entry_${entry.absolutePath}")
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, selectionShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, selectionShape)
                } else {
                    Modifier
                },
            )
            .alpha(itemAlpha)
            .semantics { onLongClick { onLongClick(); true } }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        LocalFileVisual(
            entry = entry,
            targetWidth = iconSize,
            targetHeight = iconSize,
            showThumbnails = showThumbnails,
            modifier = Modifier.size(iconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal)
            LText(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (largestSizeBytes != null && entry.metadataComplete) {
                FileSizeBar(
                    sizeBytes = entry.sizeBytes,
                    largestSizeBytes = largestSizeBytes,
                    identity = entry.absolutePath,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!entry.isReadable) LText("Neprieinama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        EntryActionsButton(entry, onPreview, onOpenWith, onSelect, onRename, onInfo, onTrash, onArchive, onAnalyze, onCopy, onMove, onBookmark, onShare, bookmarked)
    }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = iconSize + itemSpacing + 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(
    entry: FileEntry,
    detailText: String?,
    selected: Boolean,
    showThumbnails: Boolean,
    iconScalePercent: Int,
    spacingScalePercent: Int,
    gridStyle: DirectoryGridStyle,
    largestSizeBytes: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreview: () -> Unit,
    onOpenWith: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onTrash: () -> Unit,
    onArchive: () -> Unit,
    onAnalyze: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onBookmark: () -> Unit,
    bookmarked: Boolean,
    onShare: () -> Unit,
) {
    val selectionShape = RoundedCornerShape(12.dp)
    val visualHeight = (76f * iconScalePercent / 100f).dp
    val innerPadding = (9f * spacingScalePercent / 100f).dp
    val sizeBarSlotHeight = if (largestSizeBytes != null) 10.dp else 0.dp
    val extraContentHeight = sizeBarSlotHeight
    val metadata = detailText ?: if (!entry.isDirectory || largestSizeBytes != null && entry.metadataComplete) {
        FileSystemRules.humanBytes(entry.sizeBytes)
    } else {
        null
    }
    val itemAlpha = if (entry.isHidden && !selected) 0.64f else 1f
    Card(
        modifier = Modifier
            .testTag("local_entry_${entry.absolutePath}")
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, selectionShape) else Modifier)
            .alpha(itemAlpha)
            .semantics { onLongClick { onLongClick(); true } }
            .clickable(onClick = onClick),
        shape = if (gridStyle == DirectoryGridStyle.CLASSIC) RoundedCornerShape(4.dp) else selectionShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                gridStyle == DirectoryGridStyle.CLASSIC -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        DirectoryGridItemContent(
            title = entry.name,
            metadata = metadata,
            visualHeight = visualHeight,
            innerPadding = innerPadding,
            extraContentHeight = extraContentHeight,
            visual = {
                LocalFileVisual(
                    entry = entry,
                    targetWidth = (96f * iconScalePercent / 100f).dp,
                    targetHeight = visualHeight,
                    showThumbnails = showThumbnails,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            actions = {
                EntryActionsButton(entry, onPreview, onOpenWith, onSelect, onRename, onInfo, onTrash, onArchive, onAnalyze, onCopy, onMove, onBookmark, onShare, bookmarked)
            },
            extraContent = {
                if (largestSizeBytes != null && entry.metadataComplete) {
                    Box(modifier = Modifier.fillMaxWidth().height(sizeBarSlotHeight)) {
                        FileSizeBar(
                            sizeBytes = entry.sizeBytes,
                            largestSizeBytes = largestSizeBytes,
                            identity = entry.absolutePath,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                } else if (sizeBarSlotHeight > 0.dp) {
                    Spacer(Modifier.height(sizeBarSlotHeight))
                }
            },
        )
    }
}

@Composable
private fun EntryActionsButton(
    entry: FileEntry,
    onPreview: () -> Unit,
    onOpenWith: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onTrash: () -> Unit,
    onArchive: () -> Unit,
    onAnalyze: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    bookmarked: Boolean,
) {
    var expanded by remember(entry.absolutePath) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = uiText("Failo veiksmai: ${entry.name}"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    LText(
                        when {
                            entry.isDirectory -> "Atidaryti aplanką"
                            entry.kind == EntryKind.ARCHIVE -> "Atidaryti archyvą"
                            else -> "Peržiūrėti čia"
                        },
                    )
                },
                leadingIcon = {
                    Icon(
                        when {
                            entry.isDirectory -> Icons.Rounded.Folder
                            entry.kind == EntryKind.ARCHIVE -> Icons.Rounded.Archive
                            else -> Icons.Rounded.Visibility
                        },
                        contentDescription = null,
                    )
                },
                onClick = { expanded = false; onPreview() },
                enabled = entry.isReadable,
            )
            if (!entry.isDirectory) {
                DropdownMenuItem(
                    text = { LText("Atidaryti su kita programa") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    onClick = { expanded = false; onOpenWith() },
                    enabled = entry.isReadable,
                )
            }
            DropdownMenuItem(
                text = { LText("Pasirinkti") },
                leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                onClick = { expanded = false; onSelect() },
            )
            DropdownMenuItem(
                text = { LText("Kopijuoti") },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = { expanded = false; onCopy() },
                enabled = entry.isReadable,
                modifier = Modifier.testTag("copy_entry_action"),
            )
            DropdownMenuItem(
                text = { LText("Perkelti") },
                leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) },
                onClick = { expanded = false; onMove() },
                enabled = entry.isReadable,
                modifier = Modifier.testTag("move_entry_action"),
            )
            DropdownMenuItem(
                text = { LText("Archyvuoti") },
                leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                onClick = { expanded = false; onArchive() },
                enabled = entry.isReadable,
                modifier = Modifier.testTag("archive_entry_action"),
            )
            if (entry.isDirectory && featureVisible(OptionalFeature.ANALYSIS)) {
                DropdownMenuItem(
                    text = { LText("Analizuoti dabartinį aplanką") },
                    leadingIcon = { Icon(Icons.Rounded.Analytics, contentDescription = null) },
                    onClick = { expanded = false; onAnalyze() },
                    enabled = entry.isReadable,
                    modifier = Modifier.testTag("analyze_entry_action"),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { LText("Pervadinti") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { LText("Informacija") },
                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onClick = { expanded = false; onInfo() },
            )
            DropdownMenuItem(
                text = { LText(if (bookmarked) "Pašalinti žymelę" else "Pridėti žymelę") },
                leadingIcon = { Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd, contentDescription = null) },
                onClick = { expanded = false; onBookmark() },
                modifier = Modifier.testTag("bookmark_entry_action"),
            )
            DropdownMenuItem(
                text = { LText("Bendrinti") },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = { expanded = false; onShare() },
                enabled = entry.isReadable,
                modifier = Modifier.testTag("share_entry_action"),
            )
            DropdownMenuItem(
                text = { LText("Į šiukšlinę") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onTrash() },
            )
        }
    }
}

@Composable
private fun EmptyPanel(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        LText(title, style = MaterialTheme.typography.titleMedium)
        LText(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class CreateItemType { FOLDER, FILE, ARCHIVE }

@Composable
internal fun CreateItemDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateArchive: ((String, ArchiveFormat) -> Unit)? = null,
) {
    var name by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf(CreateItemType.FOLDER) }
    var archiveFormat by remember { mutableStateOf(ArchiveFormat.ZIP) }
    val title = when (itemType) {
        CreateItemType.FOLDER -> "Naujas aplankas"
        CreateItemType.FILE -> "Naujas failas"
        CreateItemType.ARCHIVE -> "Naujas archyvas"
    }
    val icon = when (itemType) {
        CreateItemType.FOLDER -> Icons.Rounded.CreateNewFolder
        CreateItemType.FILE -> Icons.Rounded.Description
        CreateItemType.ARCHIVE -> Icons.Rounded.Archive
    }
    ShovelModalDialog(
        title = title,
        icon = icon,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create_item_dialog"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Atšaukti") }
            Button(
                onClick = {
                    when (itemType) {
                        CreateItemType.FOLDER -> onCreateFolder(name)
                        CreateItemType.FILE -> onCreateFile(name)
                        CreateItemType.ARCHIVE -> onCreateArchive?.invoke(name, archiveFormat)
                    }
                },
                enabled = name.isNotBlank(),
            ) { LText("Sukurti") }
        },
    ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = itemType == CreateItemType.FOLDER,
                        onClick = { itemType = CreateItemType.FOLDER },
                        label = { LText("Aplankas") },
                        modifier = Modifier.testTag("create_type_folder"),
                    )
                    FilterChip(
                        selected = itemType == CreateItemType.FILE,
                        onClick = { itemType = CreateItemType.FILE },
                        label = { LText("Failas") },
                        modifier = Modifier.testTag("create_type_file"),
                    )
                    if (onCreateArchive != null) {
                        FilterChip(
                            selected = itemType == CreateItemType.ARCHIVE,
                            onClick = { itemType = CreateItemType.ARCHIVE },
                            label = { LText("Archyvas") },
                            modifier = Modifier.testTag("create_type_archive"),
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { LText("Pavadinimas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_item_name"),
                )
                if (itemType == CreateItemType.ARCHIVE) {
                    LText("Formatas", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ).forEach { candidate ->
                            FilterChip(
                                selected = archiveFormat == candidate,
                                onClick = { archiveFormat = candidate },
                                label = { Text(candidate.name) },
                                modifier = Modifier.testTag("create_archive_format_${candidate.name}"),
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    ShovelModalDialog(
        title = title,
        icon = Icons.Rounded.Edit,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("text_input_dialog"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Atšaukti") }
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { LText(confirmLabel) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ArchiveDialog(
    initialName: String,
    sourcePaths: List<String>,
    loadSummary: suspend (Collection<String>) -> Result<FileSelectionSummary>,
    onDismiss: () -> Unit,
    onCreate: (String, ArchiveFormat, CharArray?, Int, Long?, Boolean) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var format by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var password by remember { mutableStateOf("") }
    var hideNames by remember { mutableStateOf(false) }
    var compressionLevel by remember { mutableStateOf(ArchiveCompressionRules.DEFAULT_LEVEL) }
    var maximumSizeMib by remember { mutableStateOf("") }
    val adjustableCompression = format == ArchiveFormat.ZIP || format == ArchiveFormat.TAR_GZ
    val maximumBytes = if (adjustableCompression) {
        runCatching { ArchiveTargetSizeRules.parseMib(maximumSizeMib) }.getOrNull()
    } else null
    val maximumSizeValid = !adjustableCompression || maximumSizeMib.isBlank() || maximumBytes != null
    val selectionSummary by produceState<FileSelectionSummary?>(initialValue = null, sourcePaths) {
        value = loadSummary(sourcePaths).getOrNull()
    }
    ShovelModalDialog(
        title = "Sukurti archyvą",
        icon = Icons.Rounded.Archive,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("archive_dialog"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Atšaukti") }
            Button(
                onClick = {
                    onCreate(
                        name,
                        format,
                        password.takeIf { (format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z) && it.isNotBlank() }
                            ?.toCharArray(),
                        compressionLevel,
                        maximumBytes,
                        hideNames && format == ArchiveFormat.SEVEN_Z && password.isNotBlank(),
                    )
                },
                enabled = name.isNotBlank() && maximumSizeValid,
            ) {
                LText("Kurti")
            }
        },
    ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { LText("Pavadinimas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("archive_name"),
                )
                LText("Formatas", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ).forEach { candidate ->
                        FilterChip(
                            selected = format == candidate,
                            onClick = { format = candidate },
                            label = { Text(candidate.name) },
                            modifier = Modifier.testTag("archive_format_${candidate.name}"),
                        )
                    }
                }
                if (format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { LText("Slaptažodis (nebūtinas, AES-256)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("archive_password"),
                    )
                }
                if (format == ArchiveFormat.SEVEN_Z && password.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            LText("Hide file names", fontWeight = FontWeight.SemiBold)
                            LText(
                                "Also encrypts the file list, so nothing about the contents is visible without the password.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = hideNames,
                            onCheckedChange = { hideNames = it },
                            modifier = Modifier.testTag("archive_hide_names"),
                        )
                    }
                }
                LText(
                    if (adjustableCompression && maximumBytes != null) "Bus naudojamas stipriausias suspaudimas"
                    else if (adjustableCompression) "Suspaudimo lygis: $compressionLevel iš 9"
                    else "Šiam formatui naudojamas numatytasis suspaudimas",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = compressionLevel.toFloat(),
                    onValueChange = { compressionLevel = it.roundToInt().coerceIn(0, 9) },
                    valueRange = 0f..9f,
                    steps = 8,
                    enabled = adjustableCompression && maximumBytes == null,
                    modifier = Modifier.fillMaxWidth().testTag("archive_compression_level"),
                )
                if (adjustableCompression) {
                    OutlinedTextField(
                        value = maximumSizeMib,
                        onValueChange = { value -> maximumSizeMib = value.filter(Char::isDigit).take(4) },
                        label = { LText("Didžiausias archyvo dydis MiB (nebūtinas)") },
                        supportingText = {
                            if (!maximumSizeValid) LText("Įveskite skaičių nuo 1 iki 8192")
                            else LText("Jei ribos pasiekti nepavyks, archyvas nebus paliktas")
                        },
                        isError = !maximumSizeValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("archive_maximum_size_mib"),
                    )
                }
                selectionSummary?.let { summary ->
                    LText(
                        "Šaltinio dydis: ${FileSystemRules.humanBytes(summary.totalBytes)} · failų: ${summary.fileCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!summary.complete) {
                        LText("Dydis dalinis, nes pasiekta saugaus skenavimo riba", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LText(
                    "Galutinis archyvo dydis priklauso nuo duomenų ir bus žinomas baigus kurti.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LText("Pasirinkta: ${format.name}", style = MaterialTheme.typography.bodySmall)
            }
    }
}

@Composable
private fun TransferOptionsDialog(
    moving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ConflictPolicy, TransferVerification, TransferFailurePolicy) -> Unit,
) {
    var policy by remember { mutableStateOf(ConflictPolicy.KEEP_BOTH) }
    var verifySha256 by remember { mutableStateOf(moving) }
    var skipErrors by remember { mutableStateOf(false) }
    val choices = listOf(
        ConflictPolicy.KEEP_BOTH to "Palikti abu",
        ConflictPolicy.REPLACE to "Pakeisti esamą",
        ConflictPolicy.SKIP to "Praleisti sutampančius",
        ConflictPolicy.MERGE to "Sujungti aplankus",
    )
    ShovelModalDialog(
        title = if (moving) "Patikimai perkelti" else "Patikimai kopijuoti",
        icon = if (moving) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("transfer_options_dialog"),
        actions = {
            TextButton(onClick = onDismiss) { LText("Atšaukti") }
            Button(
                onClick = {
                    onConfirm(
                        policy,
                        if (verifySha256) TransferVerification.SHA256 else TransferVerification.SIZE,
                        if (!moving && skipErrors) TransferFailurePolicy.SKIP_AND_CONTINUE else TransferFailurePolicy.STOP,
                    )
                },
            ) { LText("Išsaugoti planą ir pradėti") }
        },
    ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LText("Planas bus išsaugotas prieš vykdymą. Ši taisyklė bus taikoma visiems sutampantiems vardams.")
                choices.forEach { (candidate, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { policy = candidate }, onLongClick = {}),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = policy == candidate, onClick = { policy = candidate })
                        LText(label)
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        LText("Patikrinti SHA-256", fontWeight = FontWeight.SemiBold)
                        LText(
                            if (moving) "Rekomenduojama: šaltinis šalinamas tik patvirtinus kopiją" else "Lėčiau, bet patvirtina failo turinį",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = verifySha256, onCheckedChange = { verifySha256 = it })
                }
                if (!moving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            LText("Praleisti klaidingą failą ir tęsti", fontWeight = FontWeight.SemiBold)
                            LText("Iki 100 klaidų bus palikta galutinėje ataskaitoje", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = skipErrors, onCheckedChange = { skipErrors = it })
                    }
                } else {
                    LText("Perkėlimas klaidos atveju visada sustoja; nepatikrintas šaltinis nešalinamas.", style = MaterialTheme.typography.bodySmall)
                }
            }
    }
}

internal fun sortLabel(mode: SortMode): String = when (mode) {
    SortMode.NAME -> "Pagal pavadinimą"
    SortMode.SIZE -> "Pagal dydį"
    SortMode.MODIFIED -> "Pagal datą"
    SortMode.TYPE -> "Pagal tipą"
}

private fun entryMeta(
    entry: FileEntry,
    dateFormat: DateFormat,
    showDirectorySize: Boolean = false,
    details: String? = null,
): String {
    if (!entry.metadataComplete) return if (entry.isDirectory) "Aplankas · kraunami duomenys…" else "Kraunami duomenys…"
    val date = entry.modifiedAtMillis.takeIf { it > 0 }?.let { dateFormat.format(Date(it)) }
    val size = when {
        // The details line already carries the folder's size.
        entry.isDirectory && details != null -> "Folder"
        entry.isDirectory && showDirectorySize -> "Aplankas · ${FileSystemRules.humanBytes(entry.sizeBytes)}"
        entry.isDirectory -> "Aplankas"
        else -> FileSystemRules.humanBytes(entry.sizeBytes)
    }
    return listOfNotNull(size, details, date).joinToString(" · ")
}
