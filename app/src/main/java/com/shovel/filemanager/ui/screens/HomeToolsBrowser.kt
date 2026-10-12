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
import com.shovel.filemanager.network.NetworkProfile
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
    safLocations: List<SafLocation>,
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
    onAddSafLocation: () -> Unit,
    onAddNextcloud: () -> Unit,
    onOpenSafLocation: (SafLocation) -> Unit,
    onRenameSafLocation: (String, String) -> Unit,
    onRemoveSafLocation: (String) -> Unit,
    onOpenSystemFiles: () -> Unit,
    onCustomizeHome: () -> Unit,
    cloudProfiles: List<NetworkProfile> = emptyList(),
    onOpenCloudProfile: (NetworkProfile) -> Unit = {},
    onEditCloudProfile: (NetworkProfile) -> Unit = {},
    onRemoveCloudProfile: (String) -> Unit = {},
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
    var showCloudLocations by remember { mutableStateOf(false) }
    var editCloudLocation by remember { mutableStateOf<SafLocation?>(null) }
    var removeCloudLocation by remember { mutableStateOf<SafLocation?>(null) }
    var removeCloudProfile by remember { mutableStateOf<NetworkProfile?>(null) }
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
                        cloudCount = safLocations.size + cloudProfiles.size,
                        bookmarkCount = bookmarks.size,
                        customization = customization,
                        shortcuts = toolShortcuts,
                        onOpenTrash = onOpenTrash,
                        // Always open the provider list: even with one saved location the user
                        // still needs a visible route for adding a second cloud/provider account.
                        onOpenCloud = { showCloudLocations = true },
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

    if (showCloudLocations) {
        ShovelModalDialog(
            title = "Debesija ir teikėjų vietos",
            icon = Icons.Rounded.Cloud,
            onDismissRequest = { showCloudLocations = false },
            expandedContent = false,
            modifier = Modifier.testTag("cloud_locations_dialog"),
            actions = {
                TextButton(onClick = { showCloudLocations = false }) { LText("Uždaryti") }
            },
        ) {
            if (safLocations.isEmpty() && cloudProfiles.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LText("Teikėjo vietų dar nepridėta")
                    LText(
                        "Android suteikia Shovel prieigą tik prie čia pasirinkto teikėjo aplanko. Visai Google Drive paskyrai reikalingas atskiras Google prisijungimas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onAddSafLocation, modifier = Modifier.padding(top = 12.dp)) {
                        LText("Pridėti teikėjo aplanką")
                    }
                    OutlinedButton(
                        onClick = { showCloudLocations = false; onAddNextcloud() },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        LText("Prisijungti prie Nextcloud")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { showCloudLocations = false; onAddNextcloud() }) {
                                LText("Pridėti Nextcloud")
                            }
                            OutlinedButton(onClick = onAddSafLocation) {
                                LText("Pridėti teikėjo aplanką")
                            }
                        }
                    }
                    items(cloudProfiles, key = { "network.${it.id}" }) { profile ->
                        ProfileCard(
                            profile = profile, loading = false,
                            onConnect = { showCloudLocations = false; onOpenCloudProfile(profile) },
                            onEdit = { showCloudLocations = false; onEditCloudProfile(profile) },
                            onDelete = { removeCloudProfile = profile },
                        )
                    }
                    items(safLocations, key = SafLocation::uri) { location ->
                        Card(onClick = { showCloudLocations = false; onOpenSafLocation(location) }) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderAppVisual(
                                    packageName = location.providerPackageName,
                                    fallbackIcon = Icons.Rounded.Cloud,
                                    targetSize = 28.dp,
                                    modifier = Modifier.size(28.dp),
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(location.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        location.folderName ?: uiText(if (location.canWrite) "Pasirinkta vieta" else "Tik skaitymui"),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                SafLocationActionButtons(
                                    location = location,
                                    onEdit = { editCloudLocation = location },
                                    onRemove = { removeCloudLocation = location },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editCloudLocation?.let { location ->
        SafLocationEditDialog(
            location = location,
            onDismiss = { editCloudLocation = null },
            onSave = { title ->
                onRenameSafLocation(location.uri, title)
                editCloudLocation = null
            },
        )
    }

    removeCloudProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { removeCloudProfile = null },
            title = { LText("Pašalinti jungtį") },
            text = { Text(profile.name) },
            confirmButton = { Button(onClick = { onRemoveCloudProfile(profile.id); removeCloudProfile = null }) { LText("Pašalinti") } },
            dismissButton = { TextButton(onClick = { removeCloudProfile = null }) { LText("Atšaukti") } },
        )
    }

    removeCloudLocation?.let { location ->
        SafLocationRemovalDialog(
            location = location,
            onDismiss = { removeCloudLocation = null },
            onRemove = {
                onRemoveSafLocation(location.uri)
                removeCloudLocation = null
            },
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

    ShovelModalDialog(
        title = "Naujausi failai",
        icon = Icons.Rounded.History,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("recent_files_dialog"),
        showFooter = false,
        expandedContent = true,
        actions = {},
    ) {
        ShovelPullToRefresh(
            isRefreshing = loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            testTag = "pull_to_refresh_recent_files",
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
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
                    if (featureVisible(OptionalFeature.TOOLBAR_SEARCH) || searchVisible) IconButton(onClick = { searchVisible = !searchVisible }, modifier = Modifier.testTag("recent_search_toggle")) {
                        Icon(Icons.Rounded.Search, contentDescription = uiText(if (searchVisible) "Slėpti paiešką" else "Ieškoti naujausiuose"))
                    }
                    Box(
                        modifier = Modifier.size(48.dp).testTag("recent_sort_toggle").combinedClickable(
                            onClick = { ascending = !ascending },
                            onLongClick = { sortMenu = true },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = uiText("Keisti rikiavimo kryptį; palaikykite rikiavimo tipui"),
                        )
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            RecentFilesSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { LText(recentFilesSortLabel(option)) },
                                    onClick = { sort = option; sortMenu = false },
                                    leadingIcon = if (option == sort) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = null) }) else null,
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.testTag("recent_date_filter")) {
                        IconButton(onClick = { dateMenu = true }) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = uiText("Filtruoti pagal datą"))
                        }
                        DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                            RecentFilesDateRange.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { LText(recentDateRangeLabel(option)) },
                                    onClick = { dateRange = option; dateMenu = false },
                                    leadingIcon = if (option == dateRange) ({ Icon(Icons.Rounded.CheckCircle, contentDescription = null) }) else null,
                                )
                            }
                        }
                    }
                    LText(recentDateRangeLabel(dateRange), style = MaterialTheme.typography.labelMedium)
                }
                if (searchVisible) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("recent_files_search"),
                        singleLine = true,
                        label = { LText("Paieška") },
                        trailingIcon = if (query.isEmpty()) null else ({
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = uiText("Išvalyti paiešką"))
                            }
                        }),
                    )
                }
                if (selectedEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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
                if (visibleItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LText(if (tab == RecentFilesTab.ADDED) "Neseniai pridėtų elementų nėra" else "Neseniai atidarytų elementų nėra")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp).testTag("recent_files_all"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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

@Composable
private fun QuickLocationsHomeSection(
    locations: List<QuickLocation>,
    displaySettings: DirectoryDisplaySettings,
    onOpen: (QuickLocation) -> Unit,
    onToggleLayout: () -> Unit,
    onConfigureLayout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LText("Greitos vietos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        DirectoryLayoutButton(
            grid = displaySettings.layoutMode == DirectoryLayoutMode.GRID,
            testTag = "home_layout_toggle",
            onToggleLayout = onToggleLayout,
            onOpenSettings = onConfigureLayout,
        )
    }
    if (locations.isEmpty()) {
        LText(
            "Greitųjų vietų nerodoma",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (displaySettings.layoutMode == DirectoryLayoutMode.GRID) {
        val columns = displaySettings.gridColumns.coerceIn(1, 6)
        val spacing = (8f * displaySettings.spacingScalePercent / 100f).dp
        locations.chunked(columns).forEach { rowLocations ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowLocations.forEach { location ->
                    QuickLocationTile(
                        location = location,
                        iconScalePercent = displaySettings.iconScalePercent,
                        compact = columns >= 5,
                        modifier = Modifier.weight(1f).testTag("quick_location_${location.id}"),
                        onClick = { onOpen(location) },
                    )
                }
                repeat(columns - rowLocations.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        locations.forEach { location ->
            StorageLocationCard(
                title = location.title,
                description = if (location.virtual) "Visa saugykla" else location.path,
                icon = location.icon,
                modifier = Modifier.testTag("quick_location_${location.id}"),
                onClick = { onOpen(location) },
            )
        }
    }
}

@Composable
private fun HomeToolsSection(
    trashCount: Int,
    cloudCount: Int,
    bookmarkCount: Int,
    customization: HomeCustomization,
    shortcuts: List<QuickLocation>,
    onOpenTrash: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenShortcut: (QuickLocation) -> Unit,
) {
    LText("Įrankiai ir saugumas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    val builtInTools = listOf(
        HomeToolLocation("trash", "Šiukšlinė", itemCountLabel(trashCount), Icons.Rounded.Delete, onOpenTrash),
        HomeToolLocation("cloud", "Debesija", itemCountLabel(cloudCount), Icons.Rounded.Cloud, onOpenCloud),
        HomeToolLocation("bookmarks", "Žymelės", itemCountLabel(bookmarkCount), Icons.Rounded.Bookmark, onOpenBookmarks),
    ).associateBy(HomeToolLocation::id)
    val visibility = com.shovel.filemanager.ui.components.LocalFeatureVisibility.current
    val tools = HomeCustomizationRules.orderedToolIds(customization).filter { id ->
        val feature = when (id) {
            "cloud" -> OptionalFeature.CLOUD
            else -> null
        }
        feature == null || visibility.isVisible(feature)
    }
        .asSequence()
        .filterNot(customization.hiddenToolIds::contains)
        .mapNotNull(builtInTools::get)
        .toList() + shortcuts.map { shortcut ->
        HomeToolLocation(
            id = "shortcut.${shortcut.id}",
            title = shortcut.title,
            description = if (shortcut.virtual) "Visa saugykla" else shortcut.path,
            icon = shortcut.icon,
            onClick = { onOpenShortcut(shortcut) },
        )
    }
    tools.chunked(2).forEach { rowTools ->
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowTools.forEach { tool ->
                ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
                    onClick = tool.onClick,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 116.dp).testTag("home_tool_${tool.id}"),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        LText(tool.title, modifier = Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LText(tool.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeCustomizationDialog(
    customization: HomeCustomization,
    roots: List<StorageRoot>,
    currentPath: String,
    onDismiss: () -> Unit,
    onMoveSection: (HomeSection, Int) -> Unit,
    onMoveShortcut: (String, Int) -> Unit,
    onSetShortcutVisible: (String, Boolean) -> Unit,
    onSetShortcutSection: (String, HomeSection) -> Unit,
    onSetSectionVisible: (HomeSection, Boolean) -> Unit,
    onMoveTool: (String, Int) -> Unit,
    onSetToolVisible: (String, Boolean) -> Unit,
    onMoveStorage: (String, Int) -> Unit,
    onSetStorageVisible: (String, Boolean) -> Unit,
    onRemoveShortcut: (String) -> Unit,
    onAddShortcut: (String, String, HomeSection) -> Boolean,
) {
    var showAdd by remember { mutableStateOf(false) }
    val storageItems = remember(roots, customization.storageOrder) {
        val labels = roots.associate { root ->
            root.id to when (root.kind) {
                StorageRootKind.INTERNAL -> "Vidinė atmintis"
                StorageRootKind.SD_CARD -> root.title.ifBlank { "SD kortelė" }
                StorageRootKind.USB_STORAGE -> root.title.ifBlank { "USB saugykla" }
                StorageRootKind.REMOVABLE -> root.title.ifBlank { "Išimama saugykla" }
            }
        } + (HomeCustomizationRules.ROOT_STORAGE_ID to "Root")
        HomeCustomizationRules.orderedStorageIds(customization, labels.keys).mapNotNull { id -> labels[id]?.let { id to it } }
    }
    ShovelModalDialog(
        title = "Tvarkyti pradžios ekraną",
        icon = Icons.Rounded.Edit,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("home_customization_dialog"),
        expandedContent = true,
        actions = {
            TextButton(onClick = onDismiss) { LText("Baigti") }
        },
    ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    LText("Pridėti failo ar aplanko nuorodą")
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                item { LText("Sekcijų tvarka", style = MaterialTheme.typography.titleSmall) }
                items(customization.sectionOrder, key = HomeSection::name) { section ->
                    val index = customization.sectionOrder.indexOf(section)
                    HomeOrderRow(
                        title = homeSectionTitle(section),
                        visible = section !in customization.hiddenSections,
                        canMoveUp = index > 0,
                        canMoveDown = index < customization.sectionOrder.lastIndex,
                        onVisibleChange = { onSetSectionVisible(section, it) },
                        onMoveUp = { onMoveSection(section, -1) },
                        onMoveDown = { onMoveSection(section, 1) },
                    )
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LText("Įrankiai", style = MaterialTheme.typography.titleSmall)
                }
                items(HomeCustomizationRules.orderedToolIds(customization), key = { "tool.$it" }) { id ->
                    val ordered = HomeCustomizationRules.orderedToolIds(customization)
                    val index = ordered.indexOf(id)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = id !in customization.hiddenToolIds,
                            onCheckedChange = { onSetToolVisible(id, it) },
                        )
                        LText(homeToolTitle(id), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        IconButton(onClick = { onMoveTool(id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Perkelti aukštyn"))
                        }
                        IconButton(onClick = { onMoveTool(id, 1) }, enabled = index < ordered.lastIndex) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Perkelti žemyn"))
                        }
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LText("Saugyklos", style = MaterialTheme.typography.titleSmall)
                }
                items(storageItems, key = { it.first }) { (id, title) ->
                    val index = storageItems.indexOfFirst { it.first == id }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = id !in customization.hiddenStorageIds,
                            onCheckedChange = { onSetStorageVisible(id, it) },
                        )
                        LText(title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        IconButton(onClick = { onMoveStorage(id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Perkelti aukštyn"))
                        }
                        IconButton(onClick = { onMoveStorage(id, 1) }, enabled = index < storageItems.lastIndex) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Perkelti žemyn"))
                        }
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LText("Greitos vietos", style = MaterialTheme.typography.titleSmall)
                }
                items(customization.shortcuts, key = HomeShortcut::id) { shortcut ->
                    val index = customization.shortcuts.indexOfFirst { it.id == shortcut.id }
                    var placementMenu by remember(shortcut.id) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = shortcut.visible,
                            onCheckedChange = { onSetShortcutVisible(shortcut.id, it) },
                        )
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            if (shortcut.builtIn) LText(shortcut.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else Text(shortcut.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                shortcut.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box {
                            TextButton(onClick = { placementMenu = true }) {
                                LText(homeSectionTitle(shortcut.section), maxLines = 1)
                            }
                            DropdownMenu(expanded = placementMenu, onDismissRequest = { placementMenu = false }) {
                                HomeCustomizationRules.SHORTCUT_SECTIONS.forEach { section ->
                                    DropdownMenuItem(
                                        text = { LText(homeSectionTitle(section)) },
                                        onClick = {
                                            placementMenu = false
                                            onSetShortcutSection(shortcut.id, section)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onMoveShortcut(shortcut.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Perkelti aukštyn"))
                        }
                        IconButton(
                            onClick = { onMoveShortcut(shortcut.id, 1) },
                            enabled = index < customization.shortcuts.lastIndex,
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Perkelti žemyn"))
                        }
                        if (!shortcut.builtIn) {
                            IconButton(onClick = { onRemoveShortcut(shortcut.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = uiText("Pašalinti greitą vietą"))
                            }
                        }
                    }
                }
                }
            }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var path by remember(currentPath) { mutableStateOf(currentPath) }
        var section by remember { mutableStateOf(HomeSection.QUICK_LOCATIONS) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { LText("Pridėti greitą vietą") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { LText("Pavadinimas (nebūtina)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { LText("Failo arba aplanko kelias") },
                        singleLine = true,
                    )
                    LText("Rodyti skiltyje", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeCustomizationRules.SHORTCUT_SECTIONS.forEach { destination ->
                            FilterChip(
                                selected = section == destination,
                                onClick = { section = destination },
                                label = { LText(homeSectionTitle(destination)) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (onAddShortcut(title, path, section)) showAdd = false },
                    enabled = path.isNotBlank(),
                ) { LText("Pridėti") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { LText("Atšaukti") } },
        )
    }
}

@Composable
private fun HomeOrderRow(
    title: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = visible, onCheckedChange = onVisibleChange)
        LText(title, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Perkelti aukštyn"))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Perkelti žemyn"))
        }
    }
}

private fun homeToolTitle(id: String): String = when (id) {
    "trash" -> "Šiukšlinė"
    "plans" -> "Shovel planai"
    "cloud" -> "Debesija"
    "bookmarks" -> "Žymelės"
    else -> id
}

private fun homeSectionTitle(section: HomeSection): String = when (section) {
    HomeSection.STORAGE -> "Saugyklos"
    HomeSection.TOOLS -> "Įrankiai ir saugumas"
    HomeSection.QUICK_LOCATIONS -> "Greitos vietos"
    HomeSection.RECENT_FILES -> "Naujausi failai"
}

private fun homeShortcutIcon(shortcut: HomeShortcut): ImageVector = when (shortcut.id) {
    else -> if (File(shortcut.path).isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description
}

@Composable
private fun RecentFileCard(item: RecentFileItem, onOpen: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
        onClick = onOpen,
        modifier = Modifier.width(188.dp).height(172.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            LocalFileVisual(
                entry = item.entry,
                targetWidth = 72.dp,
                targetHeight = 68.dp,
                showThumbnails = true,
                modifier = Modifier.fillMaxWidth().height(68.dp),
            )
            Text(item.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(FileSystemRules.humanBytes(item.entry.sizeBytes), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(recentTimeLabel(context, item.recentAtMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RecentFileListItem(
    item: RecentFileItem,
    selected: Boolean,
    selectionActive: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onTrash: () -> Unit,
    onReveal: () -> Unit,
    onCopy: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var menu by remember(item.entry.absolutePath) { mutableStateOf(false) }
    ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
        onClick = { if (selectionActive) onSelect() else onOpen() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LocalFileVisual(
                entry = item.entry,
                targetWidth = 44.dp,
                targetHeight = 44.dp,
                showThumbnails = true,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${if (item.entry.isDirectory) uiText("Aplankas") else FileSystemRules.humanBytes(item.entry.sizeBytes)} · ${recentTimeLabel(context, item.recentAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("recent_file_menu")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = uiText("Daugiau veiksmų"))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { LText("Pervadinti") }, onClick = { menu = false; onRename() }, leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }, modifier = Modifier.testTag("recent_action_rename"))
                    DropdownMenuItem(text = { LText("Bendrinti") }, onClick = { menu = false; onShare() }, leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }, modifier = Modifier.testTag("recent_action_share"))
                    DropdownMenuItem(text = { LText("Perkelti į šiukšlinę") }, onClick = { menu = false; onTrash() }, leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }, modifier = Modifier.testTag("recent_action_trash"))
                    DropdownMenuItem(text = { LText("Atidaryti aplanką") }, onClick = { menu = false; onReveal() }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) }, modifier = Modifier.testTag("recent_action_reveal"))
                    DropdownMenuItem(text = { LText("Kopijuoti") }, onClick = { menu = false; onCopy(false) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }, modifier = Modifier.testTag("recent_action_copy"))
                    DropdownMenuItem(text = { LText("Perkelti") }, onClick = { menu = false; onCopy(true) }, leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) }, modifier = Modifier.testTag("recent_action_move"))
                    DropdownMenuItem(text = { LText(if (selected) "Atžymėti" else "Pasirinkti") }, onClick = { menu = false; onSelect() }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }, modifier = Modifier.testTag("recent_action_select"))
                }
            }
        }
    }
}

private fun recentTimeLabel(context: android.content.Context, timestampMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(context, timestampMillis, false).toString()

internal data class QuickLocation(
    val id: String,
    val title: String,
    val path: String,
    val icon: ImageVector,
    val virtual: Boolean = false,
)

private data class HomeStorageLocation(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val usageFraction: Float?,
    val onClick: () -> Unit,
)

private data class HomeToolLocation(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun QuickLocationTile(
    location: QuickLocation,
    iconScalePercent: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iconSize = ((if (compact) 24f else 32f) * iconScalePercent / 100f).dp
    ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 80.dp else 92.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(location.icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            LText(
                location.title,
                style = MaterialTheme.typography.labelSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StorageLocationTile(
    location: HomeStorageLocation,
    iconScalePercent: Int,
    modifier: Modifier = Modifier,
) {
    val iconSize = (34f * iconScalePercent / 100f).dp
    val animatedUsage by animateFloatAsState(
        targetValue = location.usageFraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = spring(),
        label = "storage tile usage",
    )
    ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
        onClick = location.onClick,
        modifier = modifier.fillMaxHeight().heightIn(min = 132.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(location.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
            LText(location.title, modifier = Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LText(location.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            location.usageFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { animatedUsage },
                    modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainer,
                )
                LText(
                    "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}% užimta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StorageLocationCard(
    title: String,
    description: String,
    icon: ImageVector,
    usageFraction: Float? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val animatedUsage by animateFloatAsState(
        targetValue = usageFraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = spring(),
        label = "storage usage",
    )
    ElevatedCard(
        elevation = com.shovel.filemanager.ui.theme.appearanceCardElevation(),
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(modifier = Modifier.weight(1f)) {
                LText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LText(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                usageFraction?.let {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { animatedUsage },
                            modifier = Modifier.weight(1f).height(10.dp).padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        LText(
                            "${(it.coerceIn(0f, 1f) * 100f).roundToInt()}% užimta",
                            modifier = Modifier.padding(start = 9.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun itemCountLabel(count: Int): String = if (count == 1) "1 elementas" else "$count elementų"
