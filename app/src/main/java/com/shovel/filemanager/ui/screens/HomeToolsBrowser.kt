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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import com.shovel.filemanager.ui.theme.ShovelSurface as Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shovel.filemanager.advanced.AdvancedAccessBackend
import com.shovel.filemanager.archive.ArchiveFormat
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.data.DirectoryDisplaySettings
import com.shovel.filemanager.data.RecentViewPreferences
import com.shovel.filemanager.data.UiPreferenceRepository
import com.shovel.filemanager.data.DirectoryGridStyle
import com.shovel.filemanager.data.DirectoryLayoutMode
import com.shovel.filemanager.data.HomeCustomization
import com.shovel.filemanager.data.HomeCustomizationRules
import com.shovel.filemanager.data.OptionalFeature
import com.shovel.filemanager.ui.components.featureVisible
import com.shovel.filemanager.data.HomeDisplayArea
import com.shovel.filemanager.data.HomeSection
import com.shovel.filemanager.data.HomeShortcut
import com.shovel.filemanager.data.PanelWorkspace
import com.shovel.filemanager.data.RecentFileItem
import com.shovel.filemanager.data.RecentItem
import com.shovel.filemanager.data.SafLocation
import com.shovel.filemanager.model.ClipboardMode
import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.model.SortMode
import com.shovel.filemanager.model.StorageRoot
import com.shovel.filemanager.model.StorageRootKind
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import com.shovel.filemanager.ui.AppSection
import com.shovel.filemanager.ui.FileScrollKey
import com.shovel.filemanager.ui.MainViewModel
import com.shovel.filemanager.ui.PanelComparisonStatus
import com.shovel.filemanager.ui.PanelId
import com.shovel.filemanager.ui.PanelUiState
import com.shovel.filemanager.ui.ProgressiveScrollRules
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.components.ShovelPullToRefresh
import com.shovel.filemanager.ui.components.DirectoryBrowserToolbar
import com.shovel.filemanager.ui.components.DirectoryDisplayMenuItems
import com.shovel.filemanager.ui.components.DirectoryDisplaySettingsDialog
import com.shovel.filemanager.ui.components.DirectoryGridItemContent
import com.shovel.filemanager.ui.components.DirectoryLayoutButton
import com.shovel.filemanager.ui.components.DirectoryQuickSearchField
import com.shovel.filemanager.ui.components.DirectorySearchButton
import com.shovel.filemanager.ui.components.FileInfoDialog
import com.shovel.filemanager.ui.components.FileSizeBar
import com.shovel.filemanager.ui.components.LocalFileVisual
import com.shovel.filemanager.ui.components.ProviderAppVisual
import com.shovel.filemanager.ui.components.SelectionActionDock
import com.shovel.filemanager.ui.components.SelectionHeader
import com.shovel.filemanager.ui.components.longPressDragSelect
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.UiTranslator
import com.shovel.filemanager.ui.localization.rememberLocalizedDateTimeFormat
import com.shovel.filemanager.ui.localization.uiText
import com.shovel.filemanager.ui.preview.PreviewSource
import com.shovel.filemanager.ui.preview.openWith
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
internal fun FilesHome(
    roots: List<StorageRoot>,
    recentFiles: List<RecentFileItem>,
    recentAddedFiles: List<RecentFileItem>,
    recentOpenedFiles: List<RecentFileItem>,
    recentFilesLoading: Boolean,
    recentFilesError: String?,
    storageDisplaySettings: DirectoryDisplaySettings,
    quickLocationsDisplaySettings: DirectoryDisplaySettings,
    customization: HomeCustomization,
    trashCount: Int,
    rootStorageAvailable: Boolean,
    onOpen: (QuickLocation) -> Unit,
    onOpenStorage: (StorageRoot) -> Unit,
    onOpenRoot: () -> Unit,
    onOpenRecent: (FileEntry) -> Unit,
    onRenameRecent: (FileEntry) -> Unit,
    onShareRecent: (List<FileEntry>) -> Unit,
    onTrashRecent: (List<FileEntry>) -> Unit,
    onRevealRecent: (FileEntry) -> Unit,
    onCopyRecent: (List<FileEntry>, Boolean) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCleanup: () -> Unit,
    onRefreshRecent: () -> Unit,
    onToggleLayout: (HomeDisplayArea) -> Unit,
    onConfigureLayout: (HomeDisplayArea) -> Unit,
    onOpenSystemFiles: () -> Unit,
    onCustomizeHome: () -> Unit,
) {
    fun asLocation(shortcut: HomeShortcut) =
        QuickLocation(
            id = shortcut.id,
            title = shortcut.title,
            path = shortcut.path,
            icon = homeShortcutIcon(shortcut),
            virtual = false,
        )
    val quickLocations = customization.shortcuts.filter {
        it.visible && it.section == HomeSection.QUICK_LOCATIONS
    }.map(::asLocation)
    val storageShortcuts = customization.shortcuts.filter {
        it.visible && it.section == HomeSection.STORAGE
    }.map(::asLocation)
    val toolShortcuts = customization.shortcuts.filter {
        it.visible && it.section == HomeSection.TOOLS
    }.map(::asLocation)
    var showAllRecent by remember { mutableStateOf(false) }
    val visibility = com.shovel.filemanager.ui.components.LocalFeatureVisibility.current
    var showBookmarks by remember { mutableStateOf(false) }
    val bookmarks = remember(customization.shortcuts) { customization.shortcuts.filter { !it.builtIn } }
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).widthIn(max = 760.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LText(
                        "Failų vietos",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenSystemFiles, modifier = Modifier.testTag("open_system_files")) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = uiText("Atidaryti Android sistemos failus"))
                    }
                    IconButton(onClick = onCustomizeHome, modifier = Modifier.testTag("home_customize_direct")) {
                        Icon(Icons.Rounded.Edit, contentDescription = uiText("Tvarkyti pradžios ekraną"))
                    }
                }
                LText(
                    "Pasirinkite saugyklą, kategoriją arba dažną vietą.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ShovelPullToRefresh(
            isRefreshing = recentFilesLoading,
            onRefresh = onRefreshRecent,
            modifier = Modifier.fillMaxWidth().weight(1f),
            testTag = "pull_to_refresh_home",
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
            customization.sectionOrder.filterNot(customization.hiddenSections::contains).forEach { section ->
                when (section) {
                    HomeSection.RECENT_FILES -> if (visibility.isVisible(OptionalFeature.RECENT)) RecentFilesHomeSection(
                        recentFiles = recentFiles,
                        loading = recentFilesLoading,
                        error = recentFilesError,
                        onShowAll = { showAllRecent = true },
                        onRefresh = onRefreshRecent,
                        onOpen = onOpenRecent,
                    )
                    HomeSection.STORAGE -> StorageHomeSection(
                        roots = roots,
                        customization = customization,
                        rootStorageAvailable = rootStorageAvailable,
                        displaySettings = storageDisplaySettings,
                        onOpen = onOpenStorage,
                        onOpenRoot = onOpenRoot,
                        onOpenCleanup = onOpenCleanup,
                        shortcuts = storageShortcuts,
                        onOpenShortcut = onOpen,
                        onToggleLayout = { onToggleLayout(HomeDisplayArea.STORAGE) },
                        onConfigureLayout = { onConfigureLayout(HomeDisplayArea.STORAGE) },
                    )
                    HomeSection.TOOLS -> HomeToolsSection(
                        trashCount = trashCount,
                        bookmarkCount = bookmarks.size,
                        customization = customization,
                        shortcuts = toolShortcuts,
                        onOpenTrash = onOpenTrash,
                        onOpenBookmarks = { showBookmarks = true },
                        onOpenShortcut = onOpen,
                    )
                    HomeSection.QUICK_LOCATIONS -> QuickLocationsHomeSection(
                        locations = quickLocations,
                        displaySettings = quickLocationsDisplaySettings,
                        onOpen = onOpen,
                        onToggleLayout = { onToggleLayout(HomeDisplayArea.QUICK_LOCATIONS) },
                        onConfigureLayout = { onConfigureLayout(HomeDisplayArea.QUICK_LOCATIONS) },
                    )
                }
            }
            Spacer(Modifier.height(76.dp))
                }
            }
        }
    }

    if (showAllRecent) {
        // keep showAllRecent behavior opening full-page recent dialog
        RecentFilesDialog(
            addedItems = recentAddedFiles,
            openedItems = recentOpenedFiles,
            loading = recentFilesLoading,
            onRefresh = onRefreshRecent,
            onOpen = { entry -> showAllRecent = false; onOpenRecent(entry) },
            onRename = { entry -> showAllRecent = false; onRenameRecent(entry) },
            onShare = { entries -> showAllRecent = false; onShareRecent(entries) },
            onTrash = { entries -> showAllRecent = false; onTrashRecent(entries) },
            onReveal = { entry -> showAllRecent = false; onRevealRecent(entry) },
            onCopy = onCopyRecent,
            onDismiss = { showAllRecent = false },
        )
    }

    if (showBookmarks) {
        ShovelModalDialog(
            title = "Žymelės",
            icon = Icons.Rounded.Bookmark,
            onDismissRequest = { showBookmarks = false },
            expandedContent = true,
            modifier = Modifier.testTag("bookmarks_dialog"),
            actions = { TextButton(onClick = { showBookmarks = false }) { LText("Uždaryti") } },
        ) {
            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    LText("Naudokite „Pridėti žymelę“ bet kurio failo ar aplanko meniu.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks, key = HomeShortcut::id) { shortcut ->
                        Card(onClick = {
                            showBookmarks = false
                            onOpen(
                                QuickLocation(
                                    id = shortcut.id,
                                    title = shortcut.title,
                                    path = shortcut.path,
                                    icon = homeShortcutIcon(shortcut),
                                    virtual = false,
                                ),
                            )
                        }) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(homeShortcutIcon(shortcut), contentDescription = null)
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(shortcut.title, fontWeight = FontWeight.SemiBold)
                                    Text(shortcut.path, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentFilesDialog(
    addedItems: List<RecentFileItem>,
    openedItems: List<RecentFileItem>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (FileEntry) -> Unit,
    onRename: (FileEntry) -> Unit,
    onShare: (List<FileEntry>) -> Unit,
    onTrash: (List<FileEntry>) -> Unit,
    onReveal: (FileEntry) -> Unit,
    onCopy: (List<FileEntry>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { UiPreferenceRepository(context) }
    val savedView = remember(preferences) { preferences.loadRecentView() }

    var tab by remember { mutableStateOf(RecentFilesTab.ADDED) }
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(RecentFilesSort.entries.firstOrNull { it.name == savedView.sort } ?: RecentFilesSort.RECENT) }
    var ascending by remember { mutableStateOf(savedView.ascending) }
    var sortMenu by remember { mutableStateOf(false) }
    var dateRange by remember { mutableStateOf(RecentFilesDateRange.entries.firstOrNull { it.name == savedView.dateRange } ?: RecentFilesDateRange.ALL) }
    var dateMenu by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(preferences, sort, ascending, dateRange) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            preferences.saveRecentView(RecentViewPreferences(sort.name, ascending, dateRange.name))
        }
    }

    val sourceItems = if (tab == RecentFilesTab.ADDED) addedItems else openedItems
    val visibleItems = remember(sourceItems, query, sort, ascending, dateRange) {
        RecentFilesViewRules.apply(sourceItems, query, sort, ascending, dateRange)
    }

    val knownEntries = remember(addedItems, openedItems) {
        (addedItems + openedItems).associate { it.entry.absolutePath to it.entry }
    }
    val selectedEntries = selectedPaths.mapNotNull(knownEntries::get)
    LaunchedEffect(knownEntries) { selectedPaths = selectedPaths.intersect(knownEntries.keys) }

    val listState = rememberLazyListState()

    ShovelDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        com.shovel.filemanager.ui.theme.AppearancePage(
            modifier = Modifier.fillMaxSize().testTag("recent-files-dialog"),
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                DirectoryBrowserToolbar(
                    title = "Naujausi failai",
                    path = "",
                    backEnabled = true,
                    forwardEnabled = false,
                    upEnabled = false,
                    searchActive = searchVisible,
                    grid = false,
                    testTagPrefix = "recent",
                    onBack = { onDismiss() },
                    onForward = {},
                    onUp = {},
                    onToggleSearch = {
                        searchVisible = !searchVisible
                        if (!searchVisible) query = ""
                    },
                    onToggleLayout = {},
                    onOpenSettings = {},
                ) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = uiText("Veiksmai"))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(
                                text = { LText("Atnaujinti") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                enabled = !loading,
                                onClick = { sortMenu = false; onRefresh() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { LText("Uždaryti") },
                                onClick = { sortMenu = false; onDismiss() },
                            )
                        }
                    }
                }

                if (searchVisible) {
                    DirectoryQuickSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = { searchVisible = false; query = "" },
                        filterActive = false,
                        onOpenFilter = {},
                        modifier = Modifier.testTag("recent_search_field"),
                    )
                }

                HorizontalDivider()
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                if (selectedEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        LText("Pažymėta: ${selectedEntries.size}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onCopy(selectedEntries, false) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = uiText("Kopijuoti"))
                        }
                        IconButton(onClick = { onCopy(selectedEntries, true); selectedPaths = emptySet() }) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = uiText("Perkelti"))
                        }
                        IconButton(onClick = { onShare(selectedEntries) }) {
                            Icon(Icons.Rounded.Share, contentDescription = uiText("Bendrinti"))
                        }
                        IconButton(onClick = { onTrash(selectedEntries) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = uiText("Perkelti į šiukšlinę"))
                        }
                        IconButton(onClick = { selectedPaths = emptySet() }) {
                            Icon(Icons.Rounded.Close, contentDescription = uiText("Atžymėti visus"))
                        }
                    }
                }

                ShovelPullToRefresh(
                    isRefreshing = loading,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    testTag = "pull_to_refresh_recent_files_full",
                ) {
                    when {
                        !loading && visibleItems.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LText(if (tab == RecentFilesTab.ADDED) "Neseniai pridėtų elementų nėra" else "Neseniai atidarytų elementų nėra")
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        FilterChip(
                                            selected = tab == RecentFilesTab.ADDED,
                                            onClick = { tab = RecentFilesTab.ADDED },
                                            modifier = Modifier.testTag("recent_tab_added"),
                                            label = { LText("Neseniai pridėti") },
                                        )
                                        FilterChip(
                                            selected = tab == RecentFilesTab.OPENED,
                                            onClick = { tab = RecentFilesTab.OPENED },
                                            modifier = Modifier.testTag("recent_tab_opened"),
                                            label = { LText("Neseniai atidaryti") },
                                        )

                                        Spacer(Modifier.weight(1f))

                                        LText(recentDateRangeLabel(dateRange), style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                items(visibleItems, key = { it.entry.absolutePath }) { item ->
                                    RecentFileListItem(
                                        item = item,
                                        selected = item.entry.absolutePath in selectedPaths,
                                        selectionActive = selectedPaths.isNotEmpty(),
                                        onOpen = { onOpen(item.entry) },
                                        onSelect = {
                                            selectedPaths = selectedPaths.toMutableSet().also { paths ->
                                                if (!paths.add(item.entry.absolutePath)) paths.remove(item.entry.absolutePath)
                                            }
                                        },
                                        onRename = { onRename(item.entry) },
                                        onShare = { onShare(listOf(item.entry)) },
                                        onTrash = { onTrash(listOf(item.entry)) },
                                        onReveal = { onReveal(item.entry) },
                                        onCopy = { move -> onCopy(listOf(item.entry), move) },
                                    )
                                }
                            }

                            if (visibleItems.isNotEmpty()) {
                                LazyListFastScroller(listState)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun recentFilesSortLabel(sort: RecentFilesSort): String = when (sort) {
    RecentFilesSort.RECENT -> "Pagal datą"
    RecentFilesSort.NAME -> "Pagal pavadinimą"
    RecentFilesSort.SIZE -> "Pagal dydį"
    RecentFilesSort.TYPE -> "Pagal tipą"
}

private fun recentDateRangeLabel(range: RecentFilesDateRange): String = when (range) {
    RecentFilesDateRange.ALL -> "Visos datos"
    RecentFilesDateRange.TODAY -> "Šiandien"
    RecentFilesDateRange.LAST_7_DAYS -> "Paskutinės 7 dienos"
    RecentFilesDateRange.LAST_30_DAYS -> "Paskutinės 30 dienų"
}

@Composable
private fun RecentFilesHomeSection(
    recentFiles: List<RecentFileItem>,
    loading: Boolean,
    error: String?,
    onShowAll: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (FileEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText("Naujausi failai", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (recentFiles.isNotEmpty()) TextButton(onClick = onShowAll) { LText("Rodyti visus") }
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Rounded.Refresh, contentDescription = uiText("Atnaujinti naujausius failus"))
        }
    }
    when {
        loading && recentFiles.isEmpty() -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        error != null -> LText(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        recentFiles.isEmpty() -> LText(
            "Naujausių failų dar nėra",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("recent_files_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(recentFiles.take(8), key = { it.entry.absolutePath }) { item ->
                RecentFileCard(item = item, onOpen = { onOpen(item.entry) })
            }
        }
    }
}

@Composable
private fun StorageHomeSection(
    roots: List<StorageRoot>,
    customization: HomeCustomization,
    rootStorageAvailable: Boolean,
    displaySettings: DirectoryDisplaySettings,
    onOpen: (StorageRoot) -> Unit,
    onOpenRoot: () -> Unit,
    onOpenCleanup: () -> Unit,
    shortcuts: List<QuickLocation>,
    onOpenShortcut: (QuickLocation) -> Unit,
    onToggleLayout: () -> Unit,
    onConfigureLayout: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LText("Saugyklos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("home_storage_heading"))
        if (featureVisible(OptionalFeature.ANALYSIS)) TextButton(
            onClick = onOpenCleanup,
            modifier = Modifier.testTag("analyze_storage_button"),
        ) {
            Icon(Icons.Rounded.Analytics, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            LText("Analizuoti saugyklą")
        }
        DirectoryLayoutButton(
            grid = displaySettings.layoutMode == DirectoryLayoutMode.GRID,
            testTag = "home_storage_layout_toggle",
            onToggleLayout = onToggleLayout,
            onOpenSettings = onConfigureLayout,
        )
    }
    val locationsById = roots.associate { root ->
        val usageFraction = root.totalBytes.takeIf { it > 0L }?.let { total ->
            (total - root.freeBytes).coerceIn(0L, total).toFloat() / total.toFloat()
        }
        root.id to HomeStorageLocation(
            id = root.id,
            title = when (root.kind) {
                StorageRootKind.INTERNAL -> "Vidinė atmintis"
                StorageRootKind.SD_CARD -> root.title.ifBlank { "SD kortelė" }
                StorageRootKind.USB_STORAGE -> root.title.ifBlank { "USB saugykla" }
                StorageRootKind.REMOVABLE -> root.title.ifBlank { "Išimama saugykla" }
            },
            description = "${FileSystemRules.humanBytes(root.freeBytes)} laisva iš ${FileSystemRules.humanBytes(root.totalBytes)}",
            icon = when (root.kind) {
                StorageRootKind.INTERNAL -> Icons.Rounded.Storage
                StorageRootKind.USB_STORAGE -> Icons.Rounded.Usb
                StorageRootKind.SD_CARD, StorageRootKind.REMOVABLE -> Icons.Rounded.SdStorage
            },
            usageFraction = usageFraction,
            onClick = { onOpen(root) },
        )
    }
    val rootSpace = remember {
        File("/").let { root -> root.totalSpace.coerceAtLeast(0L) to root.usableSpace.coerceAtLeast(0L) }
    }
    val rootUsageFraction = rootSpace.first.takeIf { it > 0L }?.let { total ->
        (total - rootSpace.second).coerceIn(0L, total).toFloat() / total.toFloat()
    }
    val allLocations = locationsById + (HomeCustomizationRules.ROOT_STORAGE_ID to HomeStorageLocation(
        id = HomeCustomizationRules.ROOT_STORAGE_ID,
        title = "Root",
        description = rootSpace.first.takeIf { it > 0L }?.let { total ->
            "${FileSystemRules.humanBytes(rootSpace.second)} laisva iš ${FileSystemRules.humanBytes(total)}"
        } ?: "Sistemos failai · privilegijuota prieiga",
        icon = if (rootStorageAvailable) Icons.Rounded.LockOpen else Icons.Rounded.Folder,
        usageFraction = rootUsageFraction,
        onClick = onOpenRoot,
    ))
    val locations = HomeCustomizationRules.orderedStorageIds(customization, allLocations.keys)
        .asSequence()
        .filterNot(customization.hiddenStorageIds::contains)
        .mapNotNull(allLocations::get)
        .toList()
    if (displaySettings.layoutMode == DirectoryLayoutMode.GRID) {
        val columns = displaySettings.gridColumns.coerceIn(1, 3)
        val spacing = (8f * displaySettings.spacingScalePercent / 100f).dp
        locations.chunked(columns).forEach { rowLocations ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowLocations.forEach { location ->
                    StorageLocationTile(
                        location = location,
                        iconScalePercent = displaySettings.iconScalePercent,
                        modifier = Modifier.weight(1f).then(
                            if (location.id == HomeCustomizationRules.ROOT_STORAGE_ID) Modifier.testTag("root_storage_location") else Modifier,
                        ),
                    )
                }
                repeat(columns - rowLocations.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        shortcuts.chunked(columns).forEach { rowShortcuts ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowShortcuts.forEach { shortcut ->
                    QuickLocationTile(
                        location = shortcut,
                        iconScalePercent = displaySettings.iconScalePercent,
                        compact = columns >= 3,
                        modifier = Modifier.weight(1f).testTag("storage_shortcut_${shortcut.id}"),
                        onClick = { onOpenShortcut(shortcut) },
                    )
                }
                repeat(columns - rowShortcuts.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        locations.forEach { location ->
            StorageLocationCard(
                title = location.title,
                description = location.description,
                icon = location.icon,
                usageFraction = location.usageFraction,
                onClick = location.onClick,
                modifier = if (location.id == HomeCustomizationRules.ROOT_STORAGE_ID) Modifier.testTag("root_storage_location") else Modifier,
            )
        }
        shortcuts.forEach { shortcut ->
            StorageLocationCard(
                title = shortcut.title,
                description = if (shortcut.virtual) "Visa saugykla" else shortcut.path,
                icon = shortcut.icon,
                modifier = Modifier.testTag("storage_shortcut_${shortcut.id}"),
                onClick = { onOpenShortcut(shortcut) },
            )
        }
    }
}

// (rest of file unchanged)
