package com.shovel.filemanager.ui

import com.shovel.filemanager.ui.localization.appString

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.content.ContentResolver
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shovel.filemanager.ShovelApplication
import com.shovel.filemanager.R
import com.shovel.filemanager.advanced.AdvancedAccessMode
import com.shovel.filemanager.advanced.AdvancedAccessBackend
import com.shovel.filemanager.advanced.AdvancedAccessState
import com.shovel.filemanager.advanced.ClonedAppStorage
import com.shovel.filemanager.advanced.PrivilegedPathRules
import com.shovel.filemanager.archive.ArchiveEntryInfo
import com.shovel.filemanager.archive.ArchiveFormat
import com.shovel.filemanager.archive.ArchiveCompressionRules
import com.shovel.filemanager.archive.ArchiveMutationRules
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.data.SafLocation
import com.shovel.filemanager.data.SafEntry
import com.shovel.filemanager.data.RecentItem
import com.shovel.filemanager.data.RecentFileItem
import com.shovel.filemanager.data.SavedSearch
import com.shovel.filemanager.data.SearchDraftPreferences
import com.shovel.filemanager.data.TrashBrowserEntry
import com.shovel.filemanager.data.TrashItem
import com.shovel.filemanager.data.TrashPathRules
import com.shovel.filemanager.data.TrashRetentionPeriod
import com.shovel.filemanager.data.PanelWorkspace
import com.shovel.filemanager.data.WorkspaceSession
import com.shovel.filemanager.data.WorkspaceTab
import com.shovel.filemanager.data.WorkspaceSessionRepository
import com.shovel.filemanager.cleanup.DeviceCleanupSnapshot
import com.shovel.filemanager.data.FileCategory
import com.shovel.filemanager.data.CategoryStorageScope
import com.shovel.filemanager.data.CategoryViewMode
import com.shovel.filemanager.data.DirectoryDisplayDefaults
import com.shovel.filemanager.data.DirectoryDisplaySettings
import com.shovel.filemanager.data.DirectorySortSettings
import com.shovel.filemanager.data.DirectoryGridStyle
import com.shovel.filemanager.data.DirectoryLayoutMode
import com.shovel.filemanager.data.HomeCustomization
import com.shovel.filemanager.data.HomeCustomizationRules
import com.shovel.filemanager.data.HomeDisplayArea
import com.shovel.filemanager.data.HomeSection
import com.shovel.filemanager.data.HomeShortcut
import com.shovel.filemanager.editing.EditConflict
import com.shovel.filemanager.editing.EditDestination
import com.shovel.filemanager.editing.EditDestinationRules
import com.shovel.filemanager.editing.EditExistingPolicy
import com.shovel.filemanager.editing.EditOrigin
import com.shovel.filemanager.editing.EditSaveAsConflict
import com.shovel.filemanager.editing.EditSaveAsResult
import com.shovel.filemanager.editing.EditSaveResult
import com.shovel.filemanager.editing.EditSession
import com.shovel.filemanager.editing.EditabilityRules
import com.shovel.filemanager.editing.FileRevision
import com.shovel.filemanager.editing.LineEnding
import com.shovel.filemanager.editing.TextEncoding
import com.shovel.filemanager.model.ClipboardMode
import com.shovel.filemanager.model.ClipboardSource
import com.shovel.filemanager.model.ContentFileEntry
import com.shovel.filemanager.model.ClipboardState
import com.shovel.filemanager.model.ConflictPolicy
import com.shovel.filemanager.model.DuplicateGroup
import com.shovel.filemanager.model.DirectoryContentsUsage
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.model.SearchFilters
import com.shovel.filemanager.model.SimilarImageGroup
import com.shovel.filemanager.model.SortDirection
import com.shovel.filemanager.model.SortMode
import com.shovel.filemanager.model.StorageAnalysis
import com.shovel.filemanager.model.StorageRoot
import com.shovel.filemanager.model.StorageRootKind
import com.shovel.filemanager.data.FileSelectionSummary
import com.shovel.filemanager.operations.OperationStatus
import com.shovel.filemanager.operations.OperationContext
import com.shovel.filemanager.operations.BatchRenamePreview
import com.shovel.filemanager.operations.BatchRenameSpec
import com.shovel.filemanager.operations.BatchRenameUndo
import com.shovel.filemanager.operations.DurableTransferPlanner
import com.shovel.filemanager.operations.TransferFailurePolicy
import com.shovel.filemanager.operations.TransferVerification
import com.shovel.filemanager.pdfsigning.PdfSignaturePlacement
import com.shovel.filemanager.pdfsigning.SavedSignature
import com.shovel.filemanager.pdfsigning.SignatureDrawing
import com.shovel.filemanager.workflow.ShovelAutomationRule
import com.shovel.filemanager.workflow.ShovelAutomationSchedule
import com.shovel.filemanager.workflow.ShovelDestinationRef
import com.shovel.filemanager.workflow.ShovelLocationKind
import com.shovel.filemanager.workflow.ShovelLocationRef
import com.shovel.filemanager.workflow.ShovelOperationReceipt
import com.shovel.filemanager.workflow.ShovelPlanDefinition
import com.shovel.filemanager.workflow.ShovelPreflightSummary
import com.shovel.filemanager.workflow.ShovelSourceRef
import com.shovel.filemanager.workflow.ShovelSourceKind
import com.shovel.filemanager.workflow.ShovelUndoPreview
import com.shovel.filemanager.workflow.ShovelWorkflowLimits
import com.shovel.filemanager.workflow.identityKey
import com.shovel.filemanager.ui.preview.MediaNavigationRules
import com.shovel.filemanager.ui.preview.PreviewSource
import com.shovel.filemanager.ui.preview.previewSource
import com.shovel.filemanager.ui.theme.AppColorPalette
import com.shovel.filemanager.ui.theme.AppThemeMode
import com.shovel.filemanager.search.AnalysisProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

private const val ADVANCED_STORAGE_SCROLL_OWNER = "advanced-storage"
private const val MAX_ARCHIVE_THUMBNAIL_BYTES = 32L * 1_024L * 1_024L
private const val MAX_ARCHIVE_PREVIEW_BYTES = 256L * 1_024L * 1_024L
private const val MAX_ARCHIVE_MATERIALIZED_ITEMS = 256
private const val MAX_ARCHIVE_SHARE_FILES = 1_000

enum class AppSection {
    FILES,
    ANALYZE,
    OPERATIONS,
    TOOLS,
}

data class PanelUiState(
    val path: String = Environment.getExternalStorageDirectory().absolutePath,
    val entries: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val includeHidden: Boolean = false,
    val grid: Boolean = false,
    val showThumbnails: Boolean = false,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
    val gridColumns: Int = 3,
    val gridStyle: DirectoryGridStyle = DirectoryGridStyle.CARDS,
    val loading: Boolean = false,
    val listingScannedEntries: Int = 0,
    val listingMetadataEntries: Int = 0,
    val listingTruncated: Boolean = false,
    val error: String? = null,
    val backHistory: List<String> = emptyList(),
    val forwardHistory: List<String> = emptyList(),
    val returnToHomeAtBoundary: Boolean = false,
    val scrollToTopRequest: Long = 0L,
)

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val roots: List<String> = emptyList(),
    val results: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val scannedEntries: Int = 0,
    val truncated: Boolean = false,
    val running: Boolean = false,
    val error: String? = null,
)

data class RecentFilesUiState(
    val addedItems: List<RecentFileItem> = emptyList(),
    val openedItems: List<RecentFileItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val items: List<RecentFileItem> = (addedItems + openedItems)
        .groupBy { it.entry.absolutePath }
        .mapNotNull { (_, values) -> values.maxByOrNull(RecentFileItem::recentAtMillis) }
        .sortedWith(compareByDescending<RecentFileItem> { it.recentAtMillis }.thenBy { it.entry.name.lowercase() })
        .take(com.shovel.filemanager.data.RecentFileRepository.MAX_VISIBLE_ITEMS)
}

data class BatchRenameUiState(
    val open: Boolean = false,
    val sourcePaths: List<String> = emptyList(),
    val spec: BatchRenameSpec = BatchRenameSpec(),
    val preview: BatchRenamePreview? = null,
    val running: Boolean = false,
    val error: String? = null,
)

data class AnalysisUiState(
    val analysis: StorageAnalysis? = null,
    val duplicates: List<DuplicateGroup> = emptyList(),
    val duplicateCandidatesScanned: Int = 0,
    val duplicateScanTruncated: Boolean = false,
    val similarImages: List<SimilarImageGroup> = emptyList(),
    val rootPath: String? = null,
    val rootPaths: List<String> = emptyList(),
    val allStorage: Boolean = false,
    val running: Boolean = false,
    val similarImagesRunning: Boolean = false,
    val similarImagesAnalyzed: Boolean = false,
    val similarImagesError: String? = null,
    val progress: AnalysisProgress? = null,
    val cancelled: Boolean = false,
    val error: String? = null,
)

data class DeviceCleanupUiState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val snapshot: DeviceCleanupSnapshot? = null,
    val error: String? = null,
)

private data class DeviceCleanupLoadResult(
    val snapshot: DeviceCleanupSnapshot,
    val standardScanError: String?,
)

data class FileCategoryUiState(
    val open: Boolean = false,
    val category: FileCategory? = null,
    val entries: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val grid: Boolean = false,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
    val gridColumns: Int = 3,
    val gridStyle: DirectoryGridStyle = DirectoryGridStyle.CARDS,
    val showThumbnails: Boolean = false,
    val showSystemApps: Boolean = false,
    val appScope: com.shovel.filemanager.data.InstalledAppScope = com.shovel.filemanager.data.InstalledAppScope.USER,
    val storageScope: CategoryStorageScope = CategoryStorageScope.ALL,
    val viewMode: CategoryViewMode = CategoryViewMode.FILES,
    val parentFolderPath: String? = null,
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val scannedRows: Int = 0,
    val nextOffset: Int? = null,
    val truncated: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val scrollToTopRequest: Long = 0L,
    val error: String? = null,
)

internal object AnalysisSelectionRules {
    fun canModify(roots: Collection<File>, candidate: File): Boolean = roots.any { root ->
        when {
            root.isFile -> candidate == root
            root.isDirectory -> candidate != root && FileSystemRules.isContained(root, candidate)
            else -> false
        }
    }
}

data class AdvancedBrowserUiState(
    val open: Boolean = false,
    val title: String = "Apsaugoti Android failai",
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val backHistory: List<String> = emptyList(),
    val includeHidden: Boolean = false,
    val grid: Boolean = false,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
    val gridColumns: Int = 3,
    val gridStyle: DirectoryGridStyle = DirectoryGridStyle.CARDS,
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val loading: Boolean = false,
    val error: String? = null,
)

data class ShovelClipboardState(
    val sources: List<ShovelSourceRef>,
    val moveAfterVerifiedCopies: Boolean = false,
)

data class SafBrowserUiState(
    val location: SafLocation? = null,
    val currentUri: String? = null,
    val title: String = "",
    val entries: List<SafEntry> = emptyList(),
    val backStack: List<Pair<String, String>> = emptyList(),
    val grid: Boolean = false,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
    val gridColumns: Int = 3,
    val gridStyle: DirectoryGridStyle = DirectoryGridStyle.CARDS,
    val showThumbnails: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val loading: Boolean = false,
    val error: String? = null,
)

data class TrashBrowserUiState(
    val open: Boolean = false,
    val itemId: String? = null,
    val relativePath: String = "",
    val rootName: String? = null,
    val entries: List<TrashBrowserEntry> = emptyList(),
    val grid: Boolean = false,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
    val gridColumns: Int = 3,
    val gridStyle: DirectoryGridStyle = DirectoryGridStyle.CARDS,
    val showThumbnails: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val storedItemCount: Int = 0,
    val storedItemCountComplete: Boolean = true,
    val retentionPeriod: TrashRetentionPeriod = TrashRetentionPeriod.NEVER,
    val loading: Boolean = false,
    val emptying: Boolean = false,
    val error: String? = null,
)

data class FileEditUiState(
    val sourceKey: String? = null,
    val temporaryDownload: Boolean = false,
    val session: EditSession? = null,
    val text: String? = null,
    val stagedText: String? = null,
    val encoding: TextEncoding? = null,
    val stagedEncoding: TextEncoding? = null,
    val lineEnding: LineEnding? = null,
    val stagedLineEnding: LineEnding? = null,
    val textChanged: Boolean = false,
    val preparing: Boolean = false,
    val saving: Boolean = false,
    val modifyingPdf: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val conflict: EditConflict? = null,
    val saveAsConflict: EditSaveAsConflict? = null,
    val confirmDiscard: Boolean = false,
    val closeAfterSave: Boolean = false,
) {
    val formatChanged: Boolean get() = encoding != stagedEncoding || lineEnding != stagedLineEnding
    val hasOriginChanges: Boolean get() = textChanged || formatChanged || session?.hasOriginChanges == true
    val hasUnsavedChanges: Boolean get() = textChanged || formatChanged || session?.hasUnsavedChanges == true
}

sealed interface PreviewTarget {
    data class LocalFile(val entry: FileEntry) : PreviewTarget
    data class TrashFile(val itemId: String, val entry: FileEntry) : PreviewTarget
    data class ContentFile(val entry: ContentFileEntry) : PreviewTarget
    data class PrivilegedFile(
        val entry: FileEntry,
        val cachedFile: File,
        val localOrigin: Boolean = false,
    ) : PreviewTarget
    data class Archive(val file: FileEntry, val entries: List<ArchiveEntryInfo>) : PreviewTarget
    data class ArchiveEntry(val file: FileEntry) : PreviewTarget
}

data class SignatureLibraryUiState(
    val items: List<SavedSignature> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

enum class UiMessageAction { UNDO_BATCH_RENAME }

data class UiMessage(
    val text: String,
    val error: Boolean = false,
    val action: UiMessageAction? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as ShovelApplication).graph
    private val legacyFilesHomeDisplayIdentity = "virtual:files-home"
    private val archiveMaterializationMutex = Mutex()
    private var archiveReturnTarget: PreviewTarget? = null
    private var archiveMaterializationKey: String? = null
    private var archiveMaterializationRoot: File? = null
    private val archiveMaterializedFiles = linkedMapOf<String, File>()
    private val initialPrimaryPath = Environment.getExternalStorageDirectory().absolutePath
    private val initialDownloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        .takeIf(File::isDirectory)?.absolutePath ?: initialPrimaryPath
    private val initialWorkspace = graph.workspaceSession.load(initialDownloadsPath)
    private val homeBuiltInShortcuts = emptyList<HomeShortcut>()

    private val _section = MutableStateFlow(AppSection.FILES)
    val section: StateFlow<AppSection> = _section.asStateFlow()

    val appearanceSettings = graph.appearance.settings

    private val _filesHomeVisible = MutableStateFlow(true)
    val filesHomeVisible: StateFlow<Boolean> = _filesHomeVisible.asStateFlow()

    private val _filesHomeDisplaySettings = MutableStateFlow(initialHomeDisplaySettings(HomeDisplayArea.QUICK_LOCATIONS))
    val filesHomeDisplaySettings: StateFlow<DirectoryDisplaySettings> = _filesHomeDisplaySettings.asStateFlow()

    private val _storageHomeDisplaySettings = MutableStateFlow(initialHomeDisplaySettings(HomeDisplayArea.STORAGE))
    val storageHomeDisplaySettings: StateFlow<DirectoryDisplaySettings> = _storageHomeDisplaySettings.asStateFlow()

    private val _homeCustomization = MutableStateFlow(
        runCatching { graph.navigation.homeCustomization(homeBuiltInShortcuts) }
            .getOrElse { HomeCustomizationRules.normalize(HomeCustomization(), homeBuiltInShortcuts) },
    )
    val homeCustomization: StateFlow<HomeCustomization> = _homeCustomization.asStateFlow()

    private val _tabs = MutableStateFlow(initialWorkspace.workspace)
    val tabs: StateFlow<PanelWorkspace> = _tabs.asStateFlow()

    private val fileScrollPositions = FileScrollPositionStore()

    private val _panel = MutableStateFlow(initialWorkspace.workspace.activeTab.toPanelUiState())
    val panel: StateFlow<PanelUiState> = _panel.asStateFlow()

    private val _roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val roots: StateFlow<List<StorageRoot>> = _roots.asStateFlow()

    private val _recentFiles = MutableStateFlow(RecentFilesUiState())
    val recentFiles: StateFlow<RecentFilesUiState> = _recentFiles.asStateFlow()

    private val _fileCategory = MutableStateFlow(FileCategoryUiState())
    val fileCategory: StateFlow<FileCategoryUiState> = _fileCategory.asStateFlow()

    private val _cleanupRequested = MutableStateFlow(false)
    val cleanupRequested: StateFlow<Boolean> = _cleanupRequested.asStateFlow()

    private val _deviceCleanup = MutableStateFlow(DeviceCleanupUiState())
    private var deviceCleanupJob: Job? = null
    val deviceCleanup: StateFlow<DeviceCleanupUiState> = _deviceCleanup.asStateFlow()

    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    private val _shovelClipboard = MutableStateFlow<ShovelClipboardState?>(null)
    val shovelClipboard: StateFlow<ShovelClipboardState?> = _shovelClipboard.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _searchDraftPreferences = MutableStateFlow(graph.uiPreferences.loadSearchDraft())
    val searchDraftPreferences: StateFlow<SearchDraftPreferences> = _searchDraftPreferences.asStateFlow()

    private val _featureVisibility = MutableStateFlow(graph.uiPreferences.loadFeatureVisibility())
    val featureVisibility = _featureVisibility.asStateFlow()

    fun setFeatureVisible(feature: com.shovel.filemanager.data.OptionalFeature, visible: Boolean) {
        saveFeatureVisibility(_featureVisibility.value.withVisibility(feature, visible))
    }

    fun resetFeatureVisibility() = saveFeatureVisibility(com.shovel.filemanager.data.FeatureVisibility())

    private fun saveFeatureVisibility(value: com.shovel.filemanager.data.FeatureVisibility) {
        // One small synchronous commit keeps rapid toggles ordered and never reports an unsaved choice.
        runCatching { graph.uiPreferences.saveFeatureVisibility(value) }
            .onSuccess { _featureVisibility.value = value }
            .onFailure { message("Nustatymų įrašyti nepavyko") }
    }

    private val _analysisState = MutableStateFlow(AnalysisUiState())
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    val advancedAccess: StateFlow<AdvancedAccessState> = graph.advancedAccess.state

    private val _advancedBrowser = MutableStateFlow(AdvancedBrowserUiState())
    val advancedBrowser: StateFlow<AdvancedBrowserUiState> = _advancedBrowser.asStateFlow()

    private val _batchRename = MutableStateFlow(BatchRenameUiState())
    val batchRename: StateFlow<BatchRenameUiState> = _batchRename.asStateFlow()

    private val _renameUndo = MutableStateFlow<BatchRenameUndo?>(null)
    val renameUndo: StateFlow<BatchRenameUndo?> = _renameUndo.asStateFlow()

    private val _trashItems = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = _trashItems.asStateFlow()

    private val _trashBrowser = MutableStateFlow(TrashBrowserUiState())
    val trashBrowser: StateFlow<TrashBrowserUiState> = _trashBrowser.asStateFlow()

    private val _safLocations = MutableStateFlow<List<SafLocation>>(emptyList())
    val safLocations: StateFlow<List<SafLocation>> = _safLocations.asStateFlow()

    private val _safBrowser = MutableStateFlow(SafBrowserUiState())
    val safBrowser: StateFlow<SafBrowserUiState> = _safBrowser.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentItem>>(emptyList())
    val recents: StateFlow<List<RecentItem>> = _recents.asStateFlow()

    private val _savedSearches = MutableStateFlow<List<SavedSearch>>(emptyList())
    val savedSearches: StateFlow<List<SavedSearch>> = _savedSearches.asStateFlow()

    val appLockEnabled = graph.appLock.enabled

    private val _preview = MutableStateFlow<PreviewTarget?>(null)
    val preview: StateFlow<PreviewTarget?> = _preview.asStateFlow()

    private val _fileEditState = MutableStateFlow(FileEditUiState())
    val fileEditState: StateFlow<FileEditUiState> = _fileEditState.asStateFlow()

    private val _signatureLibrary = MutableStateFlow(SignatureLibraryUiState())
    val signatureLibrary: StateFlow<SignatureLibraryUiState> = _signatureLibrary.asStateFlow()

    val operations = graph.operationManager.operations

    /** Whether the file list shows item counts and sizes under folders and archives. */
    val folderDetailsEnabled: StateFlow<Boolean> = graph.folderStats.enabled

    fun setFolderDetailsEnabled(enabled: Boolean) = graph.folderStats.setEnabled(enabled)

    suspend fun folderStats(entry: FileEntry, includeHidden: Boolean): com.shovel.filemanager.data.FolderStats? =
        graph.folderStats.statsFor(entry, includeHidden)

    /** Saved history of what each operation did, newest first. */
    val operationLogs: StateFlow<List<com.shovel.filemanager.operations.OperationLogSummary>> =
        graph.operationLogs.summaries

    /** True when full debug logging is on; false for simple logs. */
    val operationLogDebugMode: StateFlow<Boolean> = graph.operationLogs.debugMode

    fun setOperationLogDebugMode(enabled: Boolean) = graph.operationLogs.setDebugMode(enabled)

    fun clearOperationLogs() {
        graph.operationLogs.clear()
        message("Operation logs cleared")
    }

    suspend fun operationLogEvents(id: String): List<com.shovel.filemanager.operations.OperationLogEvent> =
        withContext(Dispatchers.IO) { graph.operationLogs.events(id) }

    suspend fun operationLogReport(appInfo: String): String =
        withContext(Dispatchers.IO) { graph.operationLogs.exportText(appInfo) }

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var searchJob: Job? = null
    private var analysisJob: Job? = null
    private var analysisRequestId = 0L
    private var similarImagesJob: Job? = null
    private var advancedBrowserJob: Job? = null
    private var advancedFileOpenRequestId = 0L
    private var advancedFileOpenJob: Job? = null
    private var recentFilesJob: Job? = null
    private var fileCategoryJob: Job? = null
    private var categoryFolderReturnState: FileCategoryUiState? = null
    private var safBrowserJob: Job? = null
    private var batchRenamePreviewJob: Job? = null
    private var fileEditJob: Job? = null
    private var fileEditRequestId = 0L
    private var signatureLibraryJob: Job? = null
    private var panelRefreshJob: Job? = null
    private val handledOperations = ArrayDeque<String>()
    private val workspaceSaveRequests = Channel<WorkspaceSession>(Channel.CONFLATED)
    private val searchDraftSaveRequests = Channel<SearchDraftPreferences>(Channel.CONFLATED)

    init {
        graph.applicationScope.launch {
            for (session in workspaceSaveRequests) {
                runCatching { graph.workspaceSession.save(session) }
                    .onFailure { message(it.message ?: "Darbo sesijos išsaugoti nepavyko", true) }
            }
        }
        graph.applicationScope.launch {
            for (settings in searchDraftSaveRequests) {
                runCatching { graph.uiPreferences.saveSearchDraft(settings) }
                    .onFailure { message(it.message ?: "Nustatymų įrašyti nepavyko", true) }
            }
        }
        refreshPermissionDependentState()
        refreshTrash()
        refreshSafLocations()
        refreshNavigationState()
        refreshRecentFiles()
        viewModelScope.launch {
            operations.collectLatest { snapshots ->
                snapshots.filter {
                    it.status in setOf(OperationStatus.SUCCEEDED, OperationStatus.COMPLETED_WITH_ERRORS) && it.id !in handledOperations
                }.forEach { snapshot ->
                    handledOperations.add(snapshot.id)
                    while (handledOperations.size > 128) handledOperations.removeFirst()
                    refreshPanel()
                    refreshTrash()
                    refreshRecentFiles()
                    if (_safBrowser.value.location != null) refreshSafBrowser()
                }
            }
        }
    }

    fun setSection(section: AppSection) {
        val previousSection = _section.value
        val filesDestinationReselected = SectionNavigationRules.shouldShowFilesHome(previousSection, section)
        if (filesDestinationReselected && _fileCategory.value.open) closeFileCategory()
        _section.value = section
        if (section == AppSection.FILES) {
            if (filesDestinationReselected) {
                _filesHomeVisible.value = true
            }
            refreshRecentFiles()
        }
        if (section == AppSection.TOOLS) {
            graph.advancedAccess.refreshCapabilities()
            refreshTrash()
            refreshSafLocations()
            }
    }

    fun updateSearchDraftPreferences(settings: SearchDraftPreferences) {
        if (settings == _searchDraftPreferences.value) return
        _searchDraftPreferences.value = settings
        searchDraftSaveRequests.trySend(settings)
    }

    fun setAdvancedAccessMode(mode: AdvancedAccessMode) {
        graph.advancedAccess.setMode(mode)
        if (mode == AdvancedAccessMode.OFF) closeAdvancedBrowser()
    }

    fun requestShizukuAccess() = graph.advancedAccess.requestShizukuAccess()

    fun requestRootAccess() = graph.advancedAccess.requestRootAccess()

    fun refreshAdvancedAccess() = graph.advancedAccess.refreshCapabilities()

    fun openRootFromHome() {
        val rootBackend = graph.advancedAccess.state.value.activeBackend in setOf(
            AdvancedAccessBackend.ROOT,
            AdvancedAccessBackend.SHIZUKU_ROOT,
        )
        if (rootBackend) {
            openAdvancedBrowser("/")
        } else {
            openLocalDirectoryFromHome("/")
        }
    }

    fun showFilesHome() {
        if (_fileCategory.value.open) closeFileCategory()
        _section.value = AppSection.FILES
        _filesHomeVisible.value = true
        refreshRecentFiles()
    }

    fun openAdvancedBrowser(preferredPath: String? = null) {
        advancedBrowserJob?.cancel()
        val displayDefaults = runCatching { graph.navigation.directoryDisplayDefaults() }.getOrNull()
        val rootBackend = graph.advancedAccess.state.value.activeBackend in setOf(
            AdvancedAccessBackend.ROOT,
            AdvancedAccessBackend.SHIZUKU_ROOT,
        )
        val title = if (preferredPath == "/" || (preferredPath == null && rootBackend)) "Root" else "Apsaugoti Android failai"
        _advancedBrowser.value = AdvancedBrowserUiState(
            open = true,
            title = title,
            loading = true,
            sortMode = displayDefaults?.sortMode ?: SortMode.NAME,
            sortDirection = displayDefaults?.sortDirection ?: SortDirection.ASCENDING,
        )
        advancedBrowserJob = viewModelScope.launch {
            try {
                if (!rootBackend) {
                    val accessible = graph.privilegedFiles.probeAndroidData().getOrThrow()
                    require(accessible) { "Android/data šiuo privilegijuotu režimu nepasiekiamas" }
                }
                val roots = graph.privilegedFiles.availableRoots().getOrThrow()
                val target = preferredPath ?: roots.firstOrNull()?.path
                    ?: throw IllegalStateException("Privilegijuoti aplankai nepasiekiami")
                loadAdvancedDirectory(target, pushHistory = false, resetScroll = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _advancedBrowser.update { it.copy(loading = false, error = error.message ?: "Privilegijuotos vietos atidaryti nepavyko") }
            } finally {
                advancedBrowserJob = null
            }
        }
    }

    fun closeAdvancedBrowser() {
        advancedBrowserJob?.cancel()
        advancedBrowserJob = null
        _advancedBrowser.value = AdvancedBrowserUiState()
    }

    fun navigateAdvanced(path: String) {
        advancedBrowserJob?.cancel()
        advancedBrowserJob = viewModelScope.launch {
            try {
                loadAdvancedDirectory(path, pushHistory = true, resetScroll = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _advancedBrowser.update { it.copy(loading = false, error = error.message ?: "Aplanko atidaryti nepavyko") }
            } finally {
                advancedBrowserJob = null
            }
        }
    }

    fun navigateAdvancedBack(): Boolean {
        val state = _advancedBrowser.value
        val decision = AdvancedBrowserBackRules.decide(
            currentPath = state.path,
            backHistory = state.backHistory,
            allowedRoots = graph.privilegedFiles.roots.map { it.path },
        )
        val target = decision.targetPath
        if (target == null) {
            closeAdvancedBrowser()
            return false
        }
        advancedBrowserJob?.cancel()
        advancedBrowserJob = viewModelScope.launch {
            _advancedBrowser.update {
                it.copy(
                    backHistory = if (decision.consumeHistory) it.backHistory.dropLast(1) else it.backHistory,
                    loading = true,
                    selectedPaths = emptySet(),
                    error = null,
                )
            }
            runCatching { loadAdvancedDirectory(target, pushHistory = false, resetScroll = false) }
                .onFailure { error -> _advancedBrowser.update { it.copy(loading = false, error = error.message ?: "Aplanko atidaryti nepavyko") } }
            advancedBrowserJob = null
        }
        return true
    }

    fun canNavigateAdvancedUp(): Boolean = graph.privilegedFiles.roots
        .map { it.path }
        .let { roots -> runCatching { PrivilegedPathRules.parent(_advancedBrowser.value.path, roots) }.getOrNull() != null }

    fun navigateAdvancedUp(): Boolean {
        val roots = graph.privilegedFiles.roots.map { it.path }
        val parent = runCatching { PrivilegedPathRules.parent(_advancedBrowser.value.path, roots) }.getOrNull()
            ?: return false
        navigateAdvanced(parent)
        return true
    }

    fun refreshAdvancedBrowser() {
        val state = _advancedBrowser.value
        if (!state.open || state.path.isBlank()) return
        advancedBrowserJob?.cancel()
        advancedBrowserJob = viewModelScope.launch {
            _advancedBrowser.update { it.copy(loading = true, error = null) }
            graph.privilegedFiles.list(state.path, state.includeHidden, state.sortMode, state.sortDirection).fold(
                onSuccess = { entries -> _advancedBrowser.update { it.copy(entries = entries, selectedPaths = it.selectedPaths.intersect(entries.map(FileEntry::absolutePath).toSet()), loading = false) } },
                onFailure = { error -> _advancedBrowser.update { it.copy(loading = false, error = error.message ?: "Aplanko atnaujinti nepavyko") } },
            )
            advancedBrowserJob = null
        }
    }

    fun toggleAdvancedHidden() {
        _advancedBrowser.update { it.copy(includeHidden = !it.includeHidden, selectedPaths = emptySet()) }
        refreshAdvancedBrowser()
    }

    fun toggleAdvancedLayout() {
        val state = _advancedBrowser.value
        setAdvancedDisplaySettings(
            DirectoryDisplaySettings(
                layoutMode = if (state.grid) DirectoryLayoutMode.LIST else DirectoryLayoutMode.GRID,
                iconScalePercent = state.iconScalePercent,
                spacingScalePercent = state.spacingScalePercent,
                gridColumns = state.gridColumns,
                gridStyle = state.gridStyle,
                showThumbnails = false,
            ),
        )
    }

    fun advancedScrollPosition(path: String, grid: Boolean): FileScrollPosition =
        fileScrollPositions.read(FileScrollKey(ADVANCED_STORAGE_SCROLL_OWNER, path, grid))

    fun saveAdvancedScrollPosition(
        path: String,
        grid: Boolean,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        fileScrollPositions.write(
            FileScrollKey(ADVANCED_STORAGE_SCROLL_OWNER, path, grid),
            firstVisibleItemIndex,
            firstVisibleItemScrollOffset,
        )
    }

    fun setAdvancedDisplaySettings(settings: DirectoryDisplaySettings) {
        val state = _advancedBrowser.value
        if (state.path.isBlank()) return
        val normalized = settings.copy(showThumbnails = false)
        runCatching { graph.navigation.setDirectoryDisplaySettings("privileged:${state.path}", normalized) }
            .onSuccess {
                _advancedBrowser.update { current ->
                    current.copy(
                        grid = normalized.layoutMode == DirectoryLayoutMode.GRID,
                        iconScalePercent = normalized.iconScalePercent,
                        spacingScalePercent = normalized.spacingScalePercent,
                        gridColumns = normalized.gridColumns,
                        gridStyle = normalized.gridStyle,
                    )
                }
            }
            .onFailure { message(it.message ?: "Rodinio nustatymo išsaugoti nepavyko", true) }
    }

    fun setAdvancedSort(mode: SortMode, direction: SortDirection) {
        _advancedBrowser.update { it.copy(sortMode = mode, sortDirection = direction, selectedPaths = emptySet()) }
        refreshAdvancedBrowser()
    }

    fun toggleAdvancedSelection(path: String) {
        _advancedBrowser.update { state ->
            val selection = state.selectedPaths.toMutableSet()
            if (!selection.add(path)) selection.remove(path)
            state.copy(selectedPaths = selection)
        }
    }

    fun clearAdvancedSelection() {
        _advancedBrowser.update { it.copy(selectedPaths = emptySet()) }
    }

    fun toggleSelectAllAdvanced() {
        _advancedBrowser.update { state ->
            val all = state.entries.take(PrivilegedPathRules.MAX_SELECTED_ROOTS).map(FileEntry::absolutePath).toSet()
            state.copy(selectedPaths = if (all.isNotEmpty() && state.selectedPaths.containsAll(all)) emptySet() else all)
        }
    }

    fun copyAdvancedSelection(move: Boolean, append: Boolean = false) {
        val selected = _advancedBrowser.value.entries.asSequence()
            .map(FileEntry::absolutePath)
            .filter { it in _advancedBrowser.value.selectedPaths }
            .toList()
        if (selected.isEmpty()) return
        val existing = if (append) {
            val current = _clipboard.value
            if (current == null || current.source != ClipboardSource.PRIVILEGED || current.mode != ClipboardMode.COPY) {
                message("Nėra privilegijuoto kopijavimo rinkinio, kurį būtų galima papildyti", true)
                return
            }
            current.paths
        } else emptyList()
        val merged = ClipboardMergeRules.merge(
            existing = existing,
            additional = selected,
            maximum = PrivilegedPathRules.MAX_SELECTED_ROOTS,
            key = PrivilegedPathRules::normalizeAbsolute,
        )
        _clipboard.value = ClipboardState(
            paths = merged.items,
            mode = if (move) ClipboardMode.MOVE else ClipboardMode.COPY,
            source = ClipboardSource.PRIVILEGED,
        )
        _shovelClipboard.value = null
        clearAdvancedSelection()
        message(
            if (append) "Į privilegijuotą iškarpinę įtraukta: ${merged.addedCount} · iš viso: ${merged.items.size}"
            else if (move) "Paruošta perkelti: ${merged.items.size}"
            else "Nukopijuota į iškarpinę: ${merged.items.size}",
        )
    }

    fun pasteIntoAdvanced(conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH) {
        val clipboard = _clipboard.value ?: return
        val destination = _advancedBrowser.value.path
        if (destination.isBlank()) return
        val moving = clipboard.mode == ClipboardMode.MOVE
        graph.operationManager.submit("Kopijuojama į apsaugotą Android vietą") {
            val result = when (clipboard.source) {
                ClipboardSource.LOCAL -> graph.privilegedFiles.copyFromLocal(clipboard.paths, destination, moving, conflictPolicy, this)
                ClipboardSource.PRIVILEGED -> graph.privilegedFiles.copyWithin(clipboard.paths, destination, moving, conflictPolicy, this)
            }
            note("Nukopijuota: ${result.copiedRoots} · praleista: ${result.skippedRoots}")
            if (moving && result.skippedRoots == 0) _clipboard.value = null
            refreshAdvancedBrowser()
            if (clipboard.source == ClipboardSource.LOCAL && moving) {
                refreshPanel()
            }
        }.onFailure { message(it.message ?: "Įklijavimo pradėti nepavyko", true) }
    }

    fun createAdvancedDirectory(name: String) {
        val path = _advancedBrowser.value.path
        viewModelScope.launch {
            graph.privilegedFiles.createDirectory(path, name).fold(
                onSuccess = { refreshAdvancedBrowser() },
                onFailure = { message(it.message ?: "Aplanko sukurti nepavyko", true) },
            )
        }
    }

    fun createAdvancedFile(name: String) {
        val path = _advancedBrowser.value.path
        viewModelScope.launch {
            graph.privilegedFiles.createFile(path, name).fold(
                onSuccess = { refreshAdvancedBrowser() },
                onFailure = { message(it.message ?: "Failo sukurti nepavyko", true) },
            )
        }
    }

    fun renameAdvanced(path: String, name: String) {
        viewModelScope.launch {
            graph.privilegedFiles.rename(path, name).fold(
                onSuccess = { clearAdvancedSelection(); refreshAdvancedBrowser() },
                onFailure = { message(it.message ?: "Pervadinti nepavyko", true) },
            )
        }
    }

    fun deleteAdvancedSelectionPermanently() {
        val selected = _advancedBrowser.value.selectedPaths.toList()
        if (selected.isEmpty()) return
        graph.operationManager.submit("Šalinami pasirinkti apsaugoti failai") {
            graph.privilegedFiles.deletePermanently(selected, this)
            clearAdvancedSelection()
            refreshAdvancedBrowser()
        }.onFailure { message(it.message ?: "Trynimo pradėti nepavyko", true) }
    }

    fun openAdvancedEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            navigateAdvanced(entry.absolutePath)
            return
        }
        advancedFileOpenJob?.cancel()
        val requestId = ++advancedFileOpenRequestId
        advancedFileOpenJob = viewModelScope.launch {
            try {
                val readableLocalFile = withContext(Dispatchers.IO) {
                    AdvancedPreviewRouting.directlyReadableFile(entry, ::canExposeLocalPreview)
                }
                if (readableLocalFile != null) {
                    open(graph.localFiles.toEntry(readableLocalFile))
                } else {
                    graph.privilegedFiles.stageForPreview(entry).fold(
                        onSuccess = { cached ->
                            val privilegedOrigin = runCatching {
                                com.shovel.filemanager.advanced.PrivilegedPathRules.requireWithinAllowed(
                                    entry.absolutePath, graph.privilegedFiles.roots.map { it.path }, allowRoot = false,
                                )
                            }.isSuccess
                            _preview.value = PreviewTarget.PrivilegedFile(entry, cached, localOrigin = !privilegedOrigin)
                        },
                        onFailure = { message(it.message ?: "Failo atidaryti nepavyko", true) },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message(error.message ?: "Failo atidaryti nepavyko", true)
            } finally {
                if (advancedFileOpenRequestId == requestId) advancedFileOpenJob = null
            }
        }
    }

    private suspend fun loadAdvancedDirectory(path: String, pushHistory: Boolean, resetScroll: Boolean) {
        val before = _advancedBrowser.value
        if (resetScroll && before.path != path) fileScrollPositions.reset(ADVANCED_STORAGE_SCROLL_OWNER, path)
        val display = runCatching { graph.navigation.directoryDisplaySettings("privileged:$path") }.getOrNull()
            ?: DirectoryDisplaySettings()
        _advancedBrowser.update { it.copy(loading = true, error = null, selectedPaths = emptySet()) }
        val entries = graph.privilegedFiles.list(path, before.includeHidden, before.sortMode, before.sortDirection).getOrThrow()
        _advancedBrowser.update { state ->
            val history = if (pushHistory && before.path.isNotBlank() && before.path != path) {
                (before.backHistory + before.path).takeLast(128)
            } else before.backHistory
            state.copy(
                path = path,
                entries = entries,
                backHistory = history,
                grid = display.layoutMode == DirectoryLayoutMode.GRID,
                iconScalePercent = display.iconScalePercent,
                spacingScalePercent = display.spacingScalePercent,
                gridColumns = display.gridColumns,
                gridStyle = display.gridStyle,
                loading = false,
                error = null,
            )
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        runCatching { graph.appearance.setThemeMode(mode) }
            .onFailure { message(it.message ?: "Išvaizdos nustatymo išsaugoti nepavyko", true) }
    }

    fun setColorPalette(palette: AppColorPalette) {
        runCatching { graph.appearance.setColorPalette(palette) }
            .onFailure { message(it.message ?: "Išvaizdos nustatymo išsaugoti nepavyko", true) }
    }

    fun setAmoledBlack(enabled: Boolean) {
        runCatching { graph.appearance.setAmoledBlack(enabled) }
            .onFailure { message(it.message ?: "Išvaizdos nustatymo išsaugoti nepavyko", true) }
    }

    fun setInterfaceScalePercent(percent: Int) {
        runCatching { graph.appearance.setInterfaceScalePercent(percent) }
            .onFailure { message(it.message ?: "Išvaizdos nustatymo išsaugoti nepavyko", true) }
    }


    fun refreshRecentFiles() {
        recentFilesJob?.cancel()
        recentFilesJob = viewModelScope.launch {
            _recentFiles.update { it.copy(loading = true, error = null) }
            runCatching { graph.recentFiles.snapshot() }
                .onSuccess { snapshot ->
                    _recentFiles.value = RecentFilesUiState(
                        addedItems = snapshot.added,
                        openedItems = snapshot.opened,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _recentFiles.value = RecentFilesUiState(error = error.message ?: "Naujausių failų įkelti nepavyko")
                }
        }
    }

    private fun homeDisplayIdentity(area: HomeDisplayArea): String =
        "virtual:files-home:${area.name.lowercase()}"

    private fun initialHomeDisplaySettings(area: HomeDisplayArea): DirectoryDisplaySettings {
        val saved = runCatching {
            graph.navigation.directoryDisplaySettingsOverride(homeDisplayIdentity(area))
                ?: graph.navigation.directoryDisplaySettingsOverride(legacyFilesHomeDisplayIdentity)
                ?: graph.navigation.directoryDisplayDefaults()?.settings
        }.getOrNull()
        return normalizeHomeDisplaySettings(area, saved ?: DirectoryDisplaySettings(gridColumns = 2))
    }

    private fun normalizeHomeDisplaySettings(
        area: HomeDisplayArea,
        settings: DirectoryDisplaySettings,
    ): DirectoryDisplaySettings = settings.copy(
        showThumbnails = false,
        gridColumns = settings.gridColumns.coerceIn(
            1,
            if (area == HomeDisplayArea.STORAGE) 3 else 6,
        ),
    )

    private fun homeDisplayFlow(area: HomeDisplayArea): MutableStateFlow<DirectoryDisplaySettings> = when (area) {
        HomeDisplayArea.STORAGE -> _storageHomeDisplaySettings
        HomeDisplayArea.QUICK_LOCATIONS -> _filesHomeDisplaySettings
    }

    fun toggleFilesHomeLayout() {
        toggleHomeDisplayLayout(HomeDisplayArea.QUICK_LOCATIONS)
    }

    fun toggleHomeDisplayLayout(area: HomeDisplayArea) {
        val current = homeDisplayFlow(area).value
        setHomeDisplaySettings(
            area,
            current.copy(
                layoutMode = if (current.layoutMode == DirectoryLayoutMode.GRID) {
                    DirectoryLayoutMode.LIST
                } else {
                    DirectoryLayoutMode.GRID
                },
                showThumbnails = false,
            ),
        )
    }

    fun setFilesHomeDisplaySettings(settings: DirectoryDisplaySettings) {
        setHomeDisplaySettings(HomeDisplayArea.QUICK_LOCATIONS, settings)
    }

    fun setHomeDisplaySettings(area: HomeDisplayArea, settings: DirectoryDisplaySettings) {
        val homeSettings = normalizeHomeDisplaySettings(area, settings)
        runCatching { graph.navigation.setDirectoryDisplaySettings(homeDisplayIdentity(area), homeSettings) }
            .onSuccess { homeDisplayFlow(area).value = homeSettings }
            .onFailure { message(it.message ?: "Pradžios rodinio nustatymo išsaugoti nepavyko", true) }
    }

    fun applyDirectoryDisplaySettingsToAll(
        settings: DirectoryDisplaySettings,
        requestedSortMode: SortMode?,
        requestedSortDirection: SortDirection,
    ) {
        val normalized = DirectoryDisplaySettings(
            layoutMode = settings.layoutMode,
            iconScalePercent = settings.iconScalePercent,
            spacingScalePercent = settings.spacingScalePercent,
            gridColumns = settings.gridColumns,
            gridStyle = settings.gridStyle,
            showThumbnails = settings.showThumbnails,
        )
        val previousDefaults = runCatching { graph.navigation.directoryDisplayDefaults() }.getOrNull()
        val sortMode = requestedSortMode ?: previousDefaults?.sortMode ?: SortMode.NAME
        val sortDirection = requestedSortMode?.let { requestedSortDirection }
            ?: previousDefaults?.sortDirection
            ?: SortDirection.ASCENDING
        runCatching {
            graph.navigation.setDirectoryDisplayDefaults(
                DirectoryDisplayDefaults(normalized, sortMode, sortDirection),
            )
        }.onSuccess {
            _panel.update { it.withDirectoryDisplaySettings(normalized).copy(sortMode = sortMode, sortDirection = sortDirection) }
            _fileCategory.update {
                it.withFileCategoryDisplaySettings(normalized).copy(sortMode = sortMode, sortDirection = sortDirection)
            }
            _trashBrowser.update {
                it.withTrashDisplaySettings(normalized).copy(sortMode = sortMode, sortDirection = sortDirection)
            }
            _safBrowser.update {
                it.withSafDisplaySettings(normalized).copy(sortMode = sortMode, sortDirection = sortDirection)
            }
            _advancedBrowser.update {
                it.copy(
                    grid = normalized.layoutMode == DirectoryLayoutMode.GRID,
                    iconScalePercent = normalized.iconScalePercent,
                    spacingScalePercent = normalized.spacingScalePercent,
                    gridColumns = normalized.gridColumns,
                    gridStyle = normalized.gridStyle,
                    sortMode = sortMode,
                    sortDirection = sortDirection,
                )
            }
            _tabs.update { workspace -> workspace.withGlobalDisplayDefaults(normalized, sortMode, sortDirection) }
            persistWorkspace()
            refreshPanel()
            if (_advancedBrowser.value.open) refreshAdvancedBrowser()
            if (_trashBrowser.value.open) refreshTrashBrowser()
            if (_safBrowser.value.location != null) refreshSafBrowser()
            message("Rodinio nustatymai pritaikyti visiems aplankams")
        }.onFailure { error ->
            message(error.message ?: "Bendrų rodinio nustatymų išsaugoti nepavyko", true)
        }
    }

    fun currentDirectoryDisplayDefaults(): DirectoryDisplayDefaults =
        runCatching { graph.navigation.directoryDisplayDefaults() }.getOrNull()
            ?: panelState().let { state ->
                DirectoryDisplayDefaults(state.directoryDisplaySettings(), state.sortMode, state.sortDirection)
            }

    fun moveHomeSection(section: HomeSection, offset: Int) {
        updateHomeCustomization { HomeCustomizationRules.moveSection(it, section, offset) }
    }

    fun moveHomeShortcut(id: String, offset: Int) {
        updateHomeCustomization { HomeCustomizationRules.moveShortcut(it, id, offset) }
    }

    fun setHomeShortcutVisible(id: String, visible: Boolean) {
        updateHomeCustomization { HomeCustomizationRules.setShortcutVisible(it, id, visible) }
    }

    fun setHomeShortcutSection(id: String, section: HomeSection) {
        updateHomeCustomization { HomeCustomizationRules.setShortcutSection(it, id, section) }
    }

    fun setHomeSectionVisible(section: HomeSection, visible: Boolean) {
        updateHomeCustomization { HomeCustomizationRules.setSectionVisible(it, section, visible) }
    }

    fun moveHomeTool(id: String, offset: Int) {
        updateHomeCustomization { HomeCustomizationRules.moveTool(it, id, offset) }
    }

    fun setHomeToolVisible(id: String, visible: Boolean) {
        updateHomeCustomization { HomeCustomizationRules.setToolVisible(it, id, visible) }
    }

    suspend fun loadAdvancedSelectionInfo(paths: Collection<String>): Result<FileSelectionSummary> =
        graph.privilegedFiles.selectionInfo(paths)

    fun setCustomColors(colors: com.shovel.filemanager.ui.theme.CustomThemeColors): Boolean =
        runCatching { graph.appearance.setCustomColors(colors) }
            .onFailure { message(it.message ?: "Išvaizdos nustatymo išsaugoti nepavyko", true) }.isSuccess

    fun moveHomeStorage(id: String, offset: Int) {
        val available = _roots.value.map(StorageRoot::id) + HomeCustomizationRules.ROOT_STORAGE_ID
        updateHomeCustomization { HomeCustomizationRules.moveStorage(it, available, id, offset) }
    }

    fun setHomeStorageVisible(id: String, visible: Boolean) {
        updateHomeCustomization { HomeCustomizationRules.setStorageVisible(it, id, visible) }
    }

    fun removeHomeShortcut(id: String) {
        updateHomeCustomization { HomeCustomizationRules.removeShortcut(it, id) }
    }

    fun addHomeShortcut(title: String, path: String, section: HomeSection = HomeSection.QUICK_LOCATIONS): Boolean {
        return runCatching {
            val file = File(path.trim()).canonicalFile
            require(file.exists()) { "Tokia failo ar aplanko vieta neegzistuoja" }
            val shortcutTitle = title.trim().ifEmpty { file.name.ifEmpty { file.absolutePath } }
            val shortcut = HomeShortcut(
                id = "custom.${UUID.randomUUID()}",
                title = shortcutTitle,
                path = file.absolutePath,
                section = section,
            )
            val updated = HomeCustomizationRules.addShortcut(_homeCustomization.value, shortcut)
            _homeCustomization.value = graph.navigation.setHomeCustomization(updated, homeBuiltInShortcuts)
        }.fold(
            onSuccess = { true },
            onFailure = {
                message(it.message ?: "Greitos vietos pridėti nepavyko", true)
                false
            },
        )
    }

    private fun updateHomeCustomization(transform: (HomeCustomization) -> HomeCustomization) {
        runCatching {
            graph.navigation.setHomeCustomization(transform(_homeCustomization.value), homeBuiltInShortcuts)
        }.onSuccess { _homeCustomization.value = it }
            .onFailure { message(it.message ?: "Pradžios ekrano nustatymų išsaugoti nepavyko", true) }
    }

    fun refreshPermissionDependentState() {
        viewModelScope.launch {
            _roots.value = graph.localFiles.roots()
            refreshPanel()
        }
    }

    fun refreshStorageRoots() {
        viewModelScope.launch {
            val previous = _roots.value
            val refreshed = graph.localFiles.roots()
            _roots.value = refreshed
            val removedRoots = previous.filter { old ->
                old.kind != StorageRootKind.INTERNAL && refreshed.none { current -> sameStoragePath(old.path, current.path) }
            }
            if (removedRoots.isEmpty()) return@launch
            val fallback = refreshed.firstOrNull { it.kind == StorageRootKind.INTERNAL } ?: refreshed.firstOrNull()
            if (fallback == null) return@launch
            val panelPath = _panel.value.path
            if (removedRoots.any { removed -> storagePathContains(removed.path, panelPath) }) {
                navigate(fallback.path, rememberHistory = false)
            }
        }
    }

    private fun sameStoragePath(first: String, second: String): Boolean =
        normalizedStoragePath(first) == normalizedStoragePath(second)

    private fun storagePathContains(root: String, candidate: String): Boolean {
        val normalizedRoot = normalizedStoragePath(root)
        val normalizedCandidate = normalizedStoragePath(candidate)
        return normalizedCandidate == normalizedRoot || normalizedCandidate.startsWith("$normalizedRoot/")
    }

    private fun normalizedStoragePath(value: String): String {
        val normalized = value.replace('\\', '/').trimEnd('/')
        return normalized.ifEmpty { "/" }
    }

    fun navigate(path: String, rememberHistory: Boolean = true) {
        val target = runCatching { File(path).canonicalPath }.getOrElse {
            message(it.message ?: "Kelias nepasiekiamas", true)
            return
        }
        _filesHomeVisible.value = false
        val displaySettings = savedDirectoryDisplaySettings(target)
        val sortSettings = savedDirectorySortSettings(target)
        val currentState = _panel.value
        if (currentState.path != target) {
            fileScrollPositions.reset(_tabs.value.activeTabId, target)
        }
        _panel.update { state ->
            if (state.path == target) return@update state.copy(selectedPaths = emptySet())
            state.copy(
                path = target,
                entries = emptyList(),
                selectedPaths = emptySet(),
                loading = true,
                listingScannedEntries = 0,
                listingMetadataEntries = 0,
                listingTruncated = false,
                error = null,
                backHistory = if (rememberHistory) (state.backHistory + state.path).takeLast(50) else state.backHistory,
                forwardHistory = if (rememberHistory) emptyList() else state.forwardHistory,
            ).withDirectoryDisplaySettings(displaySettings)
                .withDirectorySortSettings(sortSettings)
        }
        syncActiveTab()
        refreshPanel()
    }

    fun fileScrollPosition(key: FileScrollKey): FileScrollPosition = fileScrollPositions.read(key)

    fun saveFileScrollPosition(key: FileScrollKey, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        fileScrollPositions.write(key, firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }

    fun navigateBack(): Boolean {
        val flow = _panel
        val state = flow.value
        val target = state.backHistory.lastOrNull() ?: return false
        val displaySettings = savedDirectoryDisplaySettings(target)
        val sortSettings = savedDirectorySortSettings(target)
        flow.update {
            it.copy(
                path = target,
                entries = emptyList(),
                selectedPaths = emptySet(),
                loading = true,
                listingScannedEntries = 0,
                listingMetadataEntries = 0,
                listingTruncated = false,
                error = null,
                backHistory = it.backHistory.dropLast(1),
                forwardHistory = (it.forwardHistory + it.path).takeLast(50),
            ).withDirectoryDisplaySettings(displaySettings)
                .withDirectorySortSettings(sortSettings)
        }
        syncActiveTab()
        refreshPanel()
        return true
    }

    fun navigateForward(): Boolean {
        val flow = _panel
        val state = flow.value
        val target = state.forwardHistory.lastOrNull() ?: return false
        val displaySettings = savedDirectoryDisplaySettings(target)
        val sortSettings = savedDirectorySortSettings(target)
        flow.update {
            it.copy(
                path = target,
                entries = emptyList(),
                selectedPaths = emptySet(),
                loading = true,
                listingScannedEntries = 0,
                listingMetadataEntries = 0,
                listingTruncated = false,
                error = null,
                backHistory = (it.backHistory + it.path).takeLast(50),
                forwardHistory = it.forwardHistory.dropLast(1),
            ).withDirectoryDisplaySettings(displaySettings)
                .withDirectorySortSettings(sortSettings)
        }
        syncActiveTab()
        refreshPanel()
        return true
    }

    fun navigateUp(): Boolean {
        val state = _panel.value
        val parent = File(state.path).parentFile ?: return false
        navigate(parent.absolutePath)
        return true
    }

    fun refreshPanel(scrollToTop: Boolean = false) {
        val flow = _panel
        val snapshot = flow.value
        panelRefreshJob?.cancel()
        val job = viewModelScope.launch {
            flow.update {
                it.copy(
                    loading = true,
                    scrollToTopRequest = it.scrollToTopRequest + if (scrollToTop) 1L else 0L,
                    listingScannedEntries = 0,
                    listingMetadataEntries = 0,
                    listingTruncated = false,
                    error = null,
                )
            }
            val result = graph.localFiles.listProgressively(
                directoryPath = snapshot.path,
                includeHidden = snapshot.includeHidden,
                sortMode = snapshot.sortMode,
                sortDirection = snapshot.sortDirection,
                onProgress = { update ->
                    flow.update { current ->
                        if (current.matchesListingRequest(snapshot)) {
                            current.copy(
                                entries = update.entries,
                                loading = !update.complete,
                                listingScannedEntries = update.scannedEntries,
                                listingMetadataEntries = update.metadataEntries,
                                listingTruncated = update.truncated,
                                error = null,
                            )
                        } else current
                    }
                },
            )
            result.fold(
                onSuccess = { entries ->
                    flow.update { current ->
                        if (current.matchesListingRequest(snapshot)) {
                            current.copy(
                                entries = entries,
                                loading = false,
                                listingMetadataEntries = entries.count(FileEntry::metadataComplete),
                                error = null,
                            )
                        } else current
                    }
                },
                onFailure = { error ->
                    flow.update { current ->
                        if (current.matchesListingRequest(snapshot)) {
                            current.copy(entries = emptyList(), loading = false, error = error.message)
                        } else current
                    }
                },
            )
        }
        panelRefreshJob = job
    }

    fun toggleHidden() {
        _panel.update { it.copy(includeHidden = !it.includeHidden) }
        syncActiveTab()
        refreshPanel()
    }

    fun toggleGrid() {
        val current = _panel.value
        setDirectoryDisplaySettings(
            current.directoryDisplaySettings().copy(
                layoutMode = if (current.grid) DirectoryLayoutMode.LIST else DirectoryLayoutMode.GRID,
            ),
        )
    }

    fun toggleThumbnails() {
        val snapshot = _panel.value
        setDirectoryDisplaySettings(snapshot.directoryDisplaySettings().copy(showThumbnails = !snapshot.showThumbnails))
    }

    fun setDirectoryDisplaySettings(settings: DirectoryDisplaySettings) {
        val snapshot = _panel.value
        runCatching { graph.navigation.setDirectoryDisplaySettings(snapshot.path, settings) }
            .onSuccess {
                _panel.update { current ->
                    if (current.path == snapshot.path) current.withDirectoryDisplaySettings(settings) else current
                }
                if (_panel.value.path == snapshot.path) syncActiveTab()
            }
            .onFailure { message(it.message ?: "Katalogo vaizdo nustatymo išsaugoti nepavyko", true) }
    }

    fun setSort(mode: SortMode) {
        val snapshot = _panel.value
        val next = if (snapshot.sortMode == mode) {
            DirectorySortSettings(
                mode = mode,
                direction = if (snapshot.sortDirection == SortDirection.ASCENDING) {
                    SortDirection.DESCENDING
                } else {
                    SortDirection.ASCENDING
                },
            )
        } else {
            DirectorySortSettings(mode = mode, direction = SortDirection.ASCENDING)
        }
        runCatching { graph.navigation.setDirectorySortSettings(snapshot.path, next) }
            .onFailure {
                message(it.message ?: "Katalogo rūšiavimo nustatymo išsaugoti nepavyko", true)
                return
            }
        _panel.update {
            if (it.sortMode == mode) it.copy(sortDirection = if (it.sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING)
            else it.copy(sortMode = mode, sortDirection = SortDirection.ASCENDING)
        }
        syncActiveTab()
        refreshPanel()
    }

    fun setSort(mode: SortMode, direction: SortDirection) {
        val snapshot = _panel.value
        runCatching {
            graph.navigation.setDirectorySortSettings(snapshot.path, DirectorySortSettings(mode, direction))
        }.onFailure {
            message(it.message ?: "Katalogo rūšiavimo nustatymo išsaugoti nepavyko", true)
            return
        }
        _panel.update { it.copy(sortMode = mode, sortDirection = direction) }
        syncActiveTab()
        refreshPanel()
    }

    fun newTab() {
        syncActiveTab(persist = false)
        val tabsFlow = _tabs
        val current = tabsFlow.value
        if (current.tabs.size >= WorkspaceSessionRepository.MAX_TABS_PER_PANEL) {
            message("Viename skydelyje galima turėti iki ${WorkspaceSessionRepository.MAX_TABS_PER_PANEL} kortelių", true)
            return
        }
        val tab = graph.workspaceSession.newTab(_panel.value.path)
        tabsFlow.value = current.copy(tabs = current.tabs + tab, activeTabId = tab.id)
        _panel.value = tab.toPanelUiState()
        persistWorkspace()
        refreshPanel()
    }

    fun duplicateTab() {
        syncActiveTab(persist = false)
        val tabsFlow = _tabs
        val current = tabsFlow.value
        if (current.tabs.size >= WorkspaceSessionRepository.MAX_TABS_PER_PANEL) {
            message("Kortelių riba pasiekta", true)
            return
        }
        val source = current.activeTab
        val duplicate = source.copy(id = java.util.UUID.randomUUID().toString(), locked = false)
        tabsFlow.value = current.copy(tabs = current.tabs + duplicate, activeTabId = duplicate.id)
        _panel.value = duplicate.toPanelUiState()
        persistWorkspace()
        refreshPanel()
    }

    fun activateTab(tabId: String) {
        val tabsFlow = _tabs
        if (tabsFlow.value.activeTabId == tabId) return
        syncActiveTab(persist = false)
        val tab = tabsFlow.value.tabs.firstOrNull { it.id == tabId } ?: return
        tabsFlow.update { it.copy(activeTabId = tabId) }
        _panel.value = tab.toPanelUiState()
        persistWorkspace()
        refreshPanel()
    }

    fun toggleTabLock() {
        syncActiveTab(persist = false)
        _tabs.update { workspace ->
            workspace.copy(tabs = workspace.tabs.map { tab ->
                if (tab.id == workspace.activeTabId) tab.copy(locked = !tab.locked) else tab
            })
        }
        persistWorkspace()
    }

    fun closeActiveTab() {
        syncActiveTab(persist = false)
        val flow = _tabs
        val workspace = flow.value
        val closing = workspace.activeTab
        if (closing.locked) {
            message("Kortelė užrakinta; pirmiausia ją atrakinkite", true)
            return
        }
        if (workspace.tabs.size == 1) {
            message("Paskutinės skydelio kortelės uždaryti negalima", true)
            return
        }
        val closingIndex = workspace.tabs.indexOfFirst { it.id == closing.id }
        val remaining = workspace.tabs.filterNot { it.id == closing.id }
        val next = remaining[closingIndex.coerceAtMost(remaining.lastIndex)]
        flow.value = workspace.copy(
            tabs = remaining,
            activeTabId = next.id,
            closedTabs = (listOf(closing.copy(locked = false)) + workspace.closedTabs).take(WorkspaceSessionRepository.MAX_CLOSED_TABS),
        )
        _panel.value = next.toPanelUiState()
        persistWorkspace()
        refreshPanel()
    }

    fun restoreClosedTab() {
        val flow = _tabs
        syncActiveTab(persist = false)
        val workspace = flow.value
        val restored = workspace.closedTabs.firstOrNull() ?: run {
            message("Atkuriamų uždarytų kortelių nėra")
            return
        }
        if (workspace.tabs.size >= WorkspaceSessionRepository.MAX_TABS_PER_PANEL) {
            message("Kortelių riba pasiekta", true)
            return
        }
        flow.value = workspace.copy(
            tabs = workspace.tabs + restored,
            activeTabId = restored.id,
            closedTabs = workspace.closedTabs.drop(1),
        )
        _panel.value = restored.toPanelUiState()
        persistWorkspace()
        refreshPanel()
    }




    fun toggleSelection(path: String) {
        _panel.update { state ->
            val selection = state.selectedPaths.toMutableSet()
            if (!selection.add(path)) selection.remove(path)
            state.copy(selectedPaths = selection)
        }
    }

    fun selectAll() {
        _panel.update { it.copy(selectedPaths = it.entries.map(FileEntry::absolutePath).toSet()) }
    }

    fun selectPaths(paths: Collection<String>) {
        _panel.update { state ->
            val available = state.entries.mapTo(hashSetOf(), FileEntry::absolutePath)
            state.copy(selectedPaths = paths.filterTo(linkedSetOf(), available::contains))
        }
    }

    fun setSelection(paths: Collection<String>, selected: Boolean) {
        if (paths.isEmpty()) return
        _panel.update { state ->
            val updated = state.selectedPaths.toMutableSet()
            if (selected) updated.addAll(paths) else updated.removeAll(paths.toSet())
            state.copy(selectedPaths = updated)
        }
    }

    fun selectOnly(path: String) {
        selectPaths(listOf(path))
    }

    fun clearSelection() {
        _panel.update { it.copy(selectedPaths = emptySet()) }
    }

    fun copySelection(move: Boolean) {
        val selected = _panel.value.selectedPaths.toList()
        if (selected.isEmpty()) return
        if (setLocalClipboard(selected, move = move, append = false)) clearSelection()
    }

    fun copyEntry(path: String, move: Boolean) {
        setLocalClipboard(listOf(path), move = move, append = false)
    }

    fun copyEntries(paths: Collection<String>, move: Boolean) {
        setLocalClipboard(paths.distinct(), move = move, append = false)
    }

    fun addSelectionToClipboard() {
        val selected = _panel.value.selectedPaths.toList()
        if (selected.isEmpty()) return
        if (setLocalClipboard(selected, move = false, append = true)) clearSelection()
    }

    private fun setLocalClipboard(paths: List<String>, move: Boolean, append: Boolean): Boolean {
        val normalized = paths.mapNotNull { path ->
            runCatching { File(path).canonicalPath }.getOrNull()
        }
        if (normalized.isEmpty()) return false
        val mode = if (move) ClipboardMode.MOVE else ClipboardMode.COPY
        val additional = normalized.map { path ->
            ShovelSourceRef(
                location = ShovelLocationRef.local(path),
                displayName = File(path).name.ifBlank { path },
            ).normalized()
        }
        val existing = if (append) {
            val current = _shovelClipboard.value
            if (current == null) {
                message("Nėra kopijavimo rinkinio, kurį būtų galima papildyti", true)
                return false
            }
            if (current.moveAfterVerifiedCopies) {
                message("Iškirptų elementų negalima maišyti su „Kopijuoti daugiau“. Pradėkite naują rinkinį.", true)
                return false
            }
            current.sources
        } else {
            emptyList()
        }
        val merged = ClipboardMergeRules.merge(
            existing = existing,
            additional = additional,
            maximum = ShovelWorkflowLimits.MAX_SOURCE_ROOTS,
            key = { source -> "${source.kind}:${source.location.identityKey()}:${source.archiveEntryPath.orEmpty()}" },
        )
        _shovelClipboard.value = ShovelClipboardState(merged.items, moveAfterVerifiedCopies = move)
        val allLocal = merged.items.all { it.kind == com.shovel.filemanager.workflow.ShovelSourceKind.FILE_SYSTEM && it.location.kind == ShovelLocationKind.LOCAL }
        _clipboard.value = if (allLocal) {
            ClipboardState(merged.items.map { it.location.path }, mode, ClipboardSource.LOCAL)
        } else {
            null
        }
        if (append) {
            message(
                if (merged.addedCount == 0) {
                    "Visi pasirinkti elementai jau yra kopijavimo rinkinyje (iš viso ${merged.items.size})"
                } else {
                    "Pridėta ${merged.addedCount} · iš viso ${merged.items.size}"
                },
            )
        } else {
            message(if (move) "Paruošta perkelti: ${merged.items.size}" else "Nukopijuota į rinkinį: ${merged.items.size}")
        }
        if (merged.limitReached) {
            message("Pasiekta kopijavimo rinkinio riba: ${ShovelWorkflowLimits.MAX_SOURCE_ROOTS} elementų", true)
        }
        return true
    }

    fun openFileCategory(category: FileCategory, forceRefresh: Boolean = false) {
        categoryFolderReturnState = null
        fileCategoryJob?.cancel()
        val display = savedDirectoryDisplaySettings(fileCategoryIdentity(category))
        val sort = runCatching { graph.navigation.directorySortSettings(fileCategoryIdentity(category)) }
            .onFailure { message(it.message ?: "Kategorijos rūšiavimo nustatymo perskaityti nepavyko", true) }
            .getOrDefault(DirectorySortSettings())
        _fileCategory.value = FileCategoryUiState(
            open = true,
            category = category,
            loading = true,
            grid = display.layoutMode == DirectoryLayoutMode.GRID,
            iconScalePercent = display.iconScalePercent,
            spacingScalePercent = display.spacingScalePercent,
            gridColumns = display.gridColumns,
            gridStyle = display.gridStyle,
            showThumbnails = display.showThumbnails,
            sortMode = sort.mode,
            sortDirection = sort.direction,
        )
        loadFileCategoryPage(reset = true, forceRefresh = forceRefresh)
    }

    private fun loadFileCategoryPage(reset: Boolean, forceRefresh: Boolean = false) {
        val snapshot = _fileCategory.value
        val category = snapshot.category ?: return
        val offset = if (reset) 0 else snapshot.nextOffset ?: return
        if (!reset && (snapshot.loading || snapshot.loadingMore)) return
        val sortMode = snapshot.sortMode
        val sortDirection = snapshot.sortDirection
        val showSystemApps = snapshot.showSystemApps
        val appScope = snapshot.appScope
        val storageScope = snapshot.storageScope
        val viewMode = snapshot.viewMode
        val parentFolderPath = snapshot.parentFolderPath
        fileCategoryJob?.cancel()
        _fileCategory.update { current ->
            if (current.category != category) current else if (reset) {
                current.copy(
                    entries = emptyList(),
                    selectedPaths = emptySet(),
                    scannedRows = 0,
                    nextOffset = null,
                    truncated = false,
                    loading = true,
                    loadingMore = false,
                    error = null,
                )
            } else {
                current.copy(loadingMore = true, error = null)
            }
        }
        fileCategoryJob = viewModelScope.launch {
            try {
                var currentOffset = offset
                var refresh = forceRefresh
                var pageEntries: List<FileEntry>
                var pageScanned = 0
                var nextOffset: Int?
                var pageTruncated = false
                do {
                    val page = graph.fileCategories.loadPage(
                        category = category,
                        offset = currentOffset,
                        sortMode = sortMode,
                        sortDirection = sortDirection,
                        forceRefresh = refresh,
                        showSystemApps = showSystemApps,
                        appScope = appScope,
                        storageScope = storageScope,
                        viewMode = viewMode,
                        parentFolderPath = parentFolderPath,
                    )
                    refresh = false
                    pageEntries = page.entries
                    pageScanned = Math.addExact(pageScanned, page.scannedRows)
                    nextOffset = page.nextOffset
                    pageTruncated = pageTruncated || page.truncated
                    if (pageEntries.isEmpty() && nextOffset != null) currentOffset = nextOffset
                } while (pageEntries.isEmpty() && nextOffset != null)

                val current = _fileCategory.value
                if (!current.open || current.category != category ||
                    current.sortMode != sortMode || current.sortDirection != sortDirection ||
                    current.showSystemApps != showSystemApps ||
                    current.appScope != appScope ||
                    current.storageScope != storageScope || current.viewMode != viewMode || current.parentFolderPath != parentFolderPath
                ) return@launch
                _fileCategory.update {
                    val merged = (if (reset) pageEntries else it.entries + pageEntries)
                        .distinctBy(FileEntry::absolutePath)
                        .take(com.shovel.filemanager.data.FileCategoryRepository.MAX_RESULTS)
                    val reachedResultLimit = merged.size >= com.shovel.filemanager.data.FileCategoryRepository.MAX_RESULTS
                    it.copy(
                        entries = merged,
                        scannedRows = (if (reset) pageScanned else it.scannedRows + pageScanned)
                            .coerceAtMost(com.shovel.filemanager.data.FileCategoryRepository.MAX_QUERY_ROWS),
                        nextOffset = nextOffset.takeUnless { reachedResultLimit },
                        truncated = it.truncated || pageTruncated || (reachedResultLimit && nextOffset != null),
                        loading = false,
                        loadingMore = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _fileCategory.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = error.message ?: "Failų kategorijos atidaryti nepavyko",
                    )
                }
            }
        }
    }

    fun refreshFileCategory() {
        if (_fileCategory.value.category != null) {
            _fileCategory.update { it.copy(scrollToTopRequest = it.scrollToTopRequest + 1L) }
            loadFileCategoryPage(reset = true, forceRefresh = true)
        }
    }

    fun loadMoreFileCategory() = loadFileCategoryPage(reset = false)

    @Suppress("DEPRECATION")
    fun openFileCategoryEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            val current = _fileCategory.value
            if (current.category != null && current.category != FileCategory.INSTALLED_APPS) {
                categoryFolderReturnState = current
                _fileCategory.value = current.copy(
                    viewMode = CategoryViewMode.FILES,
                    parentFolderPath = entry.absolutePath,
                    entries = emptyList(), selectedPaths = emptySet(),
                    scrollToTopRequest = current.scrollToTopRequest + 1,
                )
                loadFileCategoryPage(reset = true)
            }
            return
        }
        if (_fileCategory.value.category != FileCategory.INSTALLED_APPS) {
            open(entry)
            return
        }
        val application = getApplication<Application>()
        runCatching {
            val packageName = entry.packageName
                ?: application.packageManager.getPackageArchiveInfo(entry.absolutePath, 0)?.packageName
                ?: error("Programos paketo nustatyti nepavyko")
            val intent = application.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { message(it.message ?: "Programos atidaryti nepavyko", true) }
    }

    fun toggleFileCategoryLayout() {
        val state = _fileCategory.value
        setFileCategoryDisplaySettings(
            state.fileCategoryDisplaySettings().copy(
                layoutMode = if (state.grid) DirectoryLayoutMode.LIST else DirectoryLayoutMode.GRID,
            ),
        )
    }

    fun toggleFileCategoryThumbnails() {
        val state = _fileCategory.value
        setFileCategoryDisplaySettings(state.fileCategoryDisplaySettings().copy(showThumbnails = !state.showThumbnails))
    }

    fun setFileCategoryDisplaySettings(settings: DirectoryDisplaySettings) {
        val category = _fileCategory.value.category ?: return
        runCatching { graph.navigation.setDirectoryDisplaySettings(fileCategoryIdentity(category), settings) }
            .onSuccess {
                _fileCategory.update { current ->
                    if (current.category == category) current.withFileCategoryDisplaySettings(settings) else current
                }
            }
            .onFailure { message(it.message ?: "Kategorijos rodinio nustatymo išsaugoti nepavyko", true) }
    }

    fun setFileCategorySort(mode: SortMode, direction: SortDirection) {
        val state = _fileCategory.value
        if (state.category == null || (state.sortMode == mode && state.sortDirection == direction)) return
        runCatching {
            graph.navigation.setDirectorySortSettings(
                fileCategoryIdentity(state.category),
                DirectorySortSettings(mode, direction),
            )
        }.onFailure {
            message(it.message ?: "Kategorijos rūšiavimo nustatymo išsaugoti nepavyko", true)
            return
        }
        _fileCategory.update {
            it.copy(
                sortMode = mode,
                sortDirection = direction,
                selectedPaths = emptySet(),
                scrollToTopRequest = it.scrollToTopRequest + 1L,
            )
        }
        loadFileCategoryPage(reset = true)
    }

    fun toggleInstalledSystemApps() {
        val state = _fileCategory.value
        if (state.category != FileCategory.INSTALLED_APPS) return
        _fileCategory.update { it.copy(showSystemApps = !it.showSystemApps,
            appScope = if (it.showSystemApps) com.shovel.filemanager.data.InstalledAppScope.USER else com.shovel.filemanager.data.InstalledAppScope.ALL,
            selectedPaths = emptySet()) }
        loadFileCategoryPage(reset = true, forceRefresh = true)
    }

    fun setInstalledAppScope(scope: com.shovel.filemanager.data.InstalledAppScope) {
        if (_fileCategory.value.category != FileCategory.INSTALLED_APPS) return
        _fileCategory.update { it.copy(appScope = scope, showSystemApps = scope != com.shovel.filemanager.data.InstalledAppScope.USER,
            selectedPaths = emptySet(), scrollToTopRequest = it.scrollToTopRequest + 1) }
        loadFileCategoryPage(reset = true)
    }

    fun openInstalledAppFolders() {
        // App data must use the real privileged browser, never an APK inventory disguised as folders.
        if (!graph.advancedAccess.state.value.connected) {
            message("Android/data prieigai reikia aktyvaus root arba Shizuku")
            _section.value = AppSection.TOOLS
            return
        }
        closeFileCategory()
        openAdvancedBrowser("${Environment.getExternalStorageDirectory().absolutePath}/Android/data")
    }

    fun closeFileCategory() {
        fileCategoryJob?.cancel()
        fileCategoryJob = null
        _fileCategory.value = FileCategoryUiState()
        categoryFolderReturnState = null
    }

    fun backFileCategory() {
        val parent = categoryFolderReturnState
        if (parent != null && _fileCategory.value.parentFolderPath != null) {
            fileCategoryJob?.cancel()
            categoryFolderReturnState = null
            _fileCategory.value = parent.copy(selectedPaths = emptySet(), loading = false, loadingMore = false)
        } else closeFileCategory()
    }

    fun openTrashFromHome() {
        openTrashBrowser()
    }

    fun openCleanupFromHome() {
        _cleanupRequested.value = true
        _section.value = AppSection.ANALYZE
        val activePath = panelState().path
        val normalizedActivePath = runCatching { File(activePath).absoluteFile.toPath().normalize() }.getOrNull()
        val analysisRoot = _roots.value
            .filter { root ->
                val normalizedRootPath = runCatching { File(root.path).absoluteFile.toPath().normalize() }.getOrNull()
                normalizedActivePath != null && normalizedRootPath != null && normalizedActivePath.startsWith(normalizedRootPath)
            }
            .maxByOrNull { it.path.length }
            ?.path
            ?: activePath
        analyze(analysisRoot)
    }

    fun consumeCleanupRequest() {
        _cleanupRequested.value = false
    }

    fun setFileCategoryView(scope: CategoryStorageScope, mode: CategoryViewMode) {
        val state = _fileCategory.value
        if (state.category == null || state.category == FileCategory.INSTALLED_APPS ||
            (state.storageScope == scope && state.viewMode == mode && state.parentFolderPath == null)) return
        categoryFolderReturnState = null
        _fileCategory.update {
            it.copy(storageScope = scope, viewMode = mode, parentFolderPath = null, selectedPaths = emptySet(),
                scrollToTopRequest = it.scrollToTopRequest + 1L)
        }
        loadFileCategoryPage(reset = true)
    }

    fun openDeviceCleanup() {
        _deviceCleanup.update { it.copy(open = true) }
        refreshDeviceCleanup()
    }

    fun closeDeviceCleanup() {
        deviceCleanupJob?.cancel()
        deviceCleanupJob = null
        _deviceCleanup.update { it.copy(open = false, loading = false) }
    }

    fun refreshDeviceCleanup() {
        if (_deviceCleanup.value.loading) return
        _deviceCleanup.update { it.copy(loading = true, error = null) }
        deviceCleanupJob = viewModelScope.launch {
            runCatching { loadDeviceCleanupSnapshot() }.fold(
                onSuccess = { result ->
                    _deviceCleanup.update {
                        it.copy(
                            loading = false,
                            snapshot = result.snapshot,
                            error = result.standardScanError,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _deviceCleanup.update { it.copy(loading = false, error = error.message ?: "Programų nuskaityti nepavyko") }
                },
            )
        }
    }

    fun forceStopApplication(packageName: String) {
        if (_deviceCleanup.value.loading) return
        _deviceCleanup.update { it.copy(loading = true, error = null) }
        deviceCleanupJob = viewModelScope.launch {
            runCatching { graph.privilegedApps.forceStop(packageName) }.fold(
                onSuccess = {
                    _deviceCleanup.update { it.copy(loading = false) }
                    message("Programa sustabdyta")
                    refreshDeviceCleanup()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _deviceCleanup.update {
                        it.copy(loading = false, error = error.message ?: "Programos sustabdyti nepavyko")
                    }
                },
            )
        }
    }

    private suspend fun loadDeviceCleanupSnapshot(): DeviceCleanupLoadResult {
        val standard = runCatching { graph.deviceCleanup.scan() }
        standard.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val privilegedAvailable = graph.advancedAccess.state.value.connected
        val privileged = if (privilegedAvailable) runCatching { graph.privilegedApps.listRunningApps() } else null
        privileged?.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val snapshot = standard.getOrElse {
            DeviceCleanupSnapshot(
                usageAccessGranted = graph.deviceCleanup.hasUsageAccess(),
                unusedApps = emptyList(),
                cachedApps = emptyList(),
                scannedApps = 0,
                cacheSizesAvailable = false,
                usageHistoryAvailable = false,
            )
        }
        return DeviceCleanupLoadResult(
            snapshot = snapshot.copy(
                runningApps = privileged?.getOrNull()?.apps.orEmpty(),
                privilegedAppAccessAvailable = privilegedAvailable,
                privilegedAppAccessError = privileged?.exceptionOrNull()?.message,
                runningAppsTruncated = privileged?.getOrNull()?.truncated == true,
            ),
            standardScanError = standard.exceptionOrNull()?.message?.takeIf(String::isNotBlank)
                ?: standard.exceptionOrNull()?.let { "Programų nuskaityti nepavyko" },
        )
    }

    fun openUsageAccessSettings() {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { message(it.message ?: "Naudojimo prieigos nustatymų atidaryti nepavyko", true) }
    }

    fun openApplicationSettings(packageName: String) {
        runCatching {
            getApplication<Application>().startActivity(
                com.shovel.filemanager.cleanup.ApplicationActions.settingsIntent(packageName),
            )
        }.onFailure { applicationActionError("Programos nustatymų atidaryti nepavyko") }
    }

    fun requestApplicationUninstall(packageName: String) {
        runCatching {
            getApplication<Application>().startActivity(
                com.shovel.filemanager.cleanup.ApplicationActions.uninstallIntent(packageName),
            )
        }.onFailure { applicationActionError("Programos šalinimo lango atidaryti nepavyko") }
    }

    private fun applicationActionError(text: String) {
        if (_deviceCleanup.value.open) _deviceCleanup.update { it.copy(error = text) }
        else message(text, true)
    }

    fun toggleFileCategorySelection(path: String) {
        _fileCategory.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            state.copy(selectedPaths = selected)
        }
    }

    fun toggleAllFileCategoryEntries(visiblePaths: Set<String>) {
        _fileCategory.update { state ->
            val allSelected = visiblePaths.isNotEmpty() && visiblePaths.all(state.selectedPaths::contains)
            state.copy(selectedPaths = if (allSelected) state.selectedPaths - visiblePaths else state.selectedPaths + visiblePaths)
        }
    }

    fun clearFileCategorySelection() {
        _fileCategory.update { it.copy(selectedPaths = emptySet()) }
    }

    fun copyFileCategorySelection(move: Boolean, append: Boolean = false) {
        val paths = _fileCategory.value.selectedPaths.toList()
        if (paths.isEmpty()) return
        if (setLocalClipboard(paths, move, append)) clearFileCategorySelection()
    }

    fun trashFileCategorySelection() {
        val state = _fileCategory.value
        if (state.category == null || state.category == FileCategory.INSTALLED_APPS) return
        val paths = state.selectedPaths.toList()
        if (paths.isEmpty()) return
        graph.operationManager.submit("Keliama į šiukšlinę") {
            graph.trash.moveToTrash(paths, this)
            clearFileCategorySelection()
            refreshFileCategory()
        }.onFailure { message(it.message ?: "Operacijos pradėti nepavyko", true) }
    }

    fun uninstallSelectedInstalledApp() {
        val state = _fileCategory.value
        if (state.category != FileCategory.INSTALLED_APPS) return
        val selected = state.entries.filter { it.absolutePath in state.selectedPaths }
        if (selected.size != 1) {
            message("Pasirinkite vieną programą, kurią norite pašalinti", true)
            return
        }
        val packageName = selected.single().packageName
        if (packageName.isNullOrBlank()) {
            message("Programos paketo nustatyti nepavyko", true)
            return
        }
        runCatching {
            getApplication<Application>().startActivity(
                com.shovel.filemanager.cleanup.ApplicationActions.uninstallIntent(packageName),
            )
        }.onSuccess {
            clearFileCategorySelection()
        }.onFailure { message(it.message ?: "Programos šalinimo lango atidaryti nepavyko", true) }
    }

    fun exportSelectedInstalledApps(destinationUri: Uri) {
        val state = _fileCategory.value
        if (state.category != FileCategory.INSTALLED_APPS) return
        val selected = state.entries.filter { it.absolutePath in state.selectedPaths }
        if (selected.isEmpty()) return
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                destinationUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        graph.operationManager.submit("Išsaugomos įdiegtos programos") {
            graph.fileCategories.exportInstalledApps(selected, destinationUri.toString(), graph.safFiles, this)
            clearFileCategorySelection()
        }.onFailure { message(it.message ?: "Programų išsaugojimo pradėti nepavyko", true) }
    }

    fun paste(
        conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        verification: TransferVerification = TransferVerification.SIZE,
        failurePolicy: TransferFailurePolicy = TransferFailurePolicy.STOP,
    ) {
        val clipboard = _clipboard.value ?: return
        val destination = _panel.value.path
        val moving = clipboard.mode == ClipboardMode.MOVE
        if (clipboard.source == ClipboardSource.PRIVILEGED) {
            graph.operationManager.submit("Kopijuojama iš apsaugotos Android vietos") {
                val result = graph.privilegedFiles.copyToLocal(
                    sourcePaths = clipboard.paths,
                    destinationDirectory = File(destination),
                    move = moving,
                    conflictPolicy = conflictPolicy,
                    operation = this,
                )
                note("Nukopijuota: ${result.copiedRoots} · praleista: ${result.skippedRoots}")
                if (moving && result.skippedRoots == 0) {
                    _clipboard.value = null
                    _shovelClipboard.value = null
                }
                refreshPanel()
                refreshRecentFiles()
                if (_advancedBrowser.value.open) refreshAdvancedBrowser()
            }.onFailure { message(it.message ?: "Operacijos pradėti nepavyko", true) }
            return
        }
        viewModelScope.launch {
            graph.durableTransfers.createAndSubmit(
                sourcePaths = clipboard.paths,
                destinationPath = destination,
                move = moving,
                conflictPolicy = conflictPolicy,
                verification = verification,
                failurePolicy = if (moving) TransferFailurePolicy.STOP else failurePolicy,
            ).onSuccess {
                if (moving) {
                    _clipboard.value = null
                    _shovelClipboard.value = null
                }
                message("Patvarus planas išsaugotas ir įtrauktas į operacijų eilę")
            }.onFailure { message(it.message ?: "Operacijos plano sukurti nepavyko", true) }
        }
    }

    fun moveSelectionToTrash() {
        val requestedState = _panel.value
        val selected = requestedState.selectedPaths.toList()
        if (selected.isEmpty()) return
        graph.operationManager.submit("Keliama į šiukšlinę") {
            graph.trash.moveToTrash(selected, this)
            clearSelection()
            if (_panel.value.path == requestedState.path) refreshPanel()
            refreshTrash()
        }.onFailure { message(it.message ?: "Operacijos pradėti nepavyko", true) }
    }

    fun moveLocalPathsToTrash(paths: Collection<String>) {
        val selected = paths.asSequence().mapNotNull { path ->
            runCatching { File(path).canonicalFile }.getOrNull()?.takeIf(File::exists)?.absolutePath
        }.distinct().take(10_001).toList()
        if (selected.isEmpty()) return
        if (selected.size > 10_000) {
            message("Vienu metu galima tvarkyti iki 10 000 elementų", true)
            return
        }
        graph.operationManager.submit("Keliama į šiukšlinę") {
            graph.trash.moveToTrash(selected, this)
            refreshRecentFiles()
            refreshTrash()
            refreshPanel()
        }.onFailure { message(it.message ?: "Operacijos pradėti nepavyko", true) }
    }

    fun deleteSelectionPermanently() {
        val requestedState = _panel.value
        val selected = requestedState.selectedPaths.toList()
        if (selected.isEmpty()) return
        graph.operationManager.submit("Trinama visam laikui") {
            graph.localFileOperator.deletePermanently(selected, this)
            clearSelection()
            if (_panel.value.path == requestedState.path) refreshPanel()
        }.onFailure { message(it.message ?: "Operacijos pradėti nepavyko", true) }
    }

    fun createDirectory(name: String) {
        viewModelScope.launch {
            graph.localFiles.createDirectory(_panel.value.path, name).fold(
                onSuccess = { created ->
                    runCatching { graph.recentFiles.recordAdded(created.absolutePath) }
                        .onFailure { message(it.message ?: "Naujausių failų įrašo išsaugoti nepavyko", true) }
                    refreshPanel()
                    refreshRecentFiles()
                },
                onFailure = { message(it.message ?: "Aplanko sukurti nepavyko", true) },
            )
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            graph.localFiles.createEmptyFile(_panel.value.path, name).fold(
                onSuccess = { created ->
                    runCatching { graph.recentFiles.recordAdded(created.absolutePath) }
                        .onFailure { message(it.message ?: "Naujausių failų įrašo išsaugoti nepavyko", true) }
                    refreshPanel()
                    refreshRecentFiles()
                },
                onFailure = { message(it.message ?: "Failo sukurti nepavyko", true) },
            )
        }
    }

    fun rename(path: String, name: String) {
        viewModelScope.launch {
            graph.localFiles.rename(path, name).fold(
                onSuccess = { renamed ->
                    runCatching { graph.recentFiles.record(renamed.absolutePath) }
                    clearSelection()
                    refreshPanel()
                    refreshRecentFiles()
                },
                onFailure = { message(it.message ?: "Pervadinti nepavyko", true) },
            )
        }
    }

    fun beginBatchRename(paths: List<String>) {
        val sources = paths.distinct()
        if (sources.isEmpty()) return
        if (sources.size > com.shovel.filemanager.operations.BatchRenameEngine.MAX_RENAME_ITEMS) {
            message("Vienu metu galima pervadinti iki ${com.shovel.filemanager.operations.BatchRenameEngine.MAX_RENAME_ITEMS} elementų", true)
            return
        }
        _batchRename.value = BatchRenameUiState(open = true, sourcePaths = sources, running = true)
        scheduleBatchRenamePreview(immediate = true)
    }

    fun updateBatchRenameSpec(spec: BatchRenameSpec) {
        _batchRename.update { it.copy(spec = spec, preview = null, running = true, error = null) }
        scheduleBatchRenamePreview(immediate = false)
    }

    fun closeBatchRename() {
        batchRenamePreviewJob?.cancel()
        _batchRename.value = BatchRenameUiState()
    }

    fun executeBatchRename() {
        val preview = _batchRename.value.preview ?: return
        if (!preview.canExecute) return
        graph.operationManager.submit("Masinis pervadinimas") {
            val undo = graph.batchRename.execute(preview, this)
            _renameUndo.value = undo
            _panel.update { it.copy(selectedPaths = emptySet()) }
            rerunSearchIfVisible()
            message(
                "Pervadinta: ${undo.items.size}",
                action = UiMessageAction.UNDO_BATCH_RENAME,
            )
        }.onSuccess {
            closeBatchRename()
        }.onFailure { message(it.message ?: "Pervadinimo pradėti nepavyko", true) }
    }

    fun undoBatchRename() {
        val undo = _renameUndo.value ?: return
        graph.operationManager.submit("Atšaukiamas masinis pervadinimas") {
            graph.batchRename.undo(undo, this)
            _renameUndo.value = null
            rerunSearchIfVisible()
            message("Pervadinimas atšauktas")
        }.onFailure { message(it.message ?: "Atšaukimo pradėti nepavyko", true) }
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) {
            if (shouldOpenWithAdvancedAccess(entry.absolutePath)) {
                openAdvancedBrowser(entry.absolutePath)
                return
            }
            if (!entry.isReadable) {
                message("Įjunkite Root arba Shizuku root prieigą skiltyje Daugiau", true)
                return
            }
            viewModelScope.launch {
                runCatching { graph.recentFiles.record(entry.absolutePath) }
                    .onSuccess { refreshRecentFiles() }
                    .onFailure { message(it.message ?: "Naujausių failų įrašo išsaugoti nepavyko", true) }
            }
            _section.value = AppSection.FILES
            navigate(entry.absolutePath)
            return
        }
        runCatching { _recents.value = graph.navigation.recordRecent(entry.absolutePath) }
            .onFailure { message(it.message ?: "Istorijos įrašyti nepavyko", true) }
        viewModelScope.launch {
            runCatching { graph.recentFiles.record(entry.absolutePath) }
                .onSuccess { refreshRecentFiles() }
                .onFailure { message(it.message ?: "Naujausių failų įrašo išsaugoti nepavyko", true) }
        }
        viewModelScope.launch {
            when (entry.kind) {
                com.shovel.filemanager.model.EntryKind.ARCHIVE -> runCatching { graph.archives.list(entry.file) }
                    .onSuccess { _preview.value = PreviewTarget.Archive(entry, it) }
                    .onFailure { error ->
                        if (error is com.shovel.filemanager.archive.ArchivePasswordRequiredException) {
                            _archivePasswordRequest.value = entry
                        } else {
                            message(error.message ?: "Archyvo perskaityti nepavyko", true)
                        }
                    }
                else -> {
                    val directlyExposed = withContext(Dispatchers.IO) { canExposeLocalPreview(entry.file) }
                    if (directlyExposed) _preview.value = PreviewTarget.LocalFile(entry)
                    else graph.privilegedFiles.stageForPreview(entry).fold(
                        onSuccess = { cached -> _preview.value = PreviewTarget.PrivilegedFile(entry, cached, localOrigin = true) },
                        onFailure = { message(it.message ?: "Failo atidaryti nepavyko", true) },
                    )
                }
            }
        }
    }

    private fun canExposeLocalPreview(file: File): Boolean = runCatching {
        androidx.core.content.FileProvider.getUriForFile(
            getApplication(), "${getApplication<Application>().packageName}.files", file,
        )
    }.isSuccess

    fun canNavigatePreviewMedia(target: PreviewTarget): Boolean = when (target) {
        is PreviewTarget.LocalFile -> localMediaCandidates(target.entry).size > 1
        else -> false
    }

    fun backgroundMediaPlaylist(target: PreviewTarget): List<com.shovel.filemanager.media.BackgroundMediaItem> {
        if (target !is PreviewTarget.LocalFile || target.entry.kind !in setOf(EntryKind.AUDIO, EntryKind.VIDEO)) return emptyList()
        val entries = localMediaCandidates(target.entry)
        if (entries.size > 256) return emptyList()
        val items = entries.map { com.shovel.filemanager.media.BackgroundMediaItem(Uri.fromFile(it.file).toString(), it.name.take(512)) }
        return items.takeIf { values -> values.sumOf { it.uri.length + it.title.length } <= 128 * 1024 }.orEmpty()
    }

    fun navigatePreviewMedia(delta: Int) {
        if (delta == 0) return
        if (_fileEditState.value.hasUnsavedChanges || _fileEditState.value.saving || _fileEditState.value.modifyingPdf) {
            message("Pirmiausia išsaugokite arba atmeskite redagavimo pakeitimus", true)
            return
        }
        when (val target = _preview.value) {
            is PreviewTarget.LocalFile -> {
                val next = MediaNavigationRules.next(
                    localMediaCandidates(target.entry),
                    target.entry.absolutePath,
                    delta,
                    FileEntry::absolutePath,
                ) ?: return
                open(next)
            }
            else -> Unit
        }
    }

    private fun localMediaCandidates(current: FileEntry): List<FileEntry> {
        val category = _fileCategory.value
        val source = if (category.open && category.entries.any { it.absolutePath == current.absolutePath }) {
            category.entries
        } else {
            _panel.value.entries
        }
        return source.filter { entry ->
            !entry.isDirectory && entry.kind == current.kind && entry.kind in setOf(EntryKind.IMAGE, EntryKind.AUDIO, EntryKind.VIDEO)
        }
    }

    fun prepareFileEdit() {
        val target = _preview.value ?: return
        if (
            target is PreviewTarget.Archive ||
            target is PreviewTarget.ArchiveEntry
        ) return
        val source = target.previewSource()
        val existing = _fileEditState.value
        if (existing.sourceKey == source.key && (existing.session != null || existing.preparing)) return

        val requestId = ++fileEditRequestId
        fileEditJob?.cancel()
        val previousSession = existing.session
        val temporaryDownload = source is PreviewSource.Privileged
        _fileEditState.value = FileEditUiState(
            sourceKey = source.key,
            temporaryDownload = temporaryDownload,
            preparing = true,
        )
        fileEditJob = viewModelScope.launch {
            var prepared: EditSession? = null
            try {
                withContext(Dispatchers.IO) { graph.editSessions.discard(previousSession) }
                val context = getApplication<Application>()
                val mimeType = source.mimeType(context)
                val internalTextEditor = EditabilityRules.supportsInternalText(source.name, mimeType, source.kind)
                val origin = when (target) {
                    is PreviewTarget.LocalFile -> EditOrigin.Local(target.entry.absolutePath, target.entry.isWritable)
                    is PreviewTarget.TrashFile -> EditOrigin.Local(target.entry.absolutePath, target.entry.isWritable)
                    is PreviewTarget.ContentFile -> EditOrigin.Content(target.entry.uri, target.entry.isWritable)
                    is PreviewTarget.PrivilegedFile -> if (target.localOrigin) {
                        EditOrigin.Local(target.entry.absolutePath, target.entry.isWritable)
                    } else EditOrigin.Privileged(target.entry.absolutePath, target.entry.isWritable)
                    is PreviewTarget.ArchiveEntry,
                    is PreviewTarget.Archive,
                    -> error("Šio peržiūros tipo tiesiogiai redaguoti negalima")
                }
                prepared = withContext(Dispatchers.IO) {
                    when (source) {
                        is PreviewSource.Content -> graph.editSessions.prepareFromStream(
                            sourceKey = source.key,
                            displayName = source.name,
                            mimeType = mimeType,
                            origin = origin,
                            expectedSizeBytes = source.sizeBytes,
                            modifiedAtMillis = source.modifiedAtMillis,
                            internalTextEditor = internalTextEditor,
                            openSource = { source.openInputStream(context) },
                        )
                        else -> graph.editSessions.prepareFromFile(
                            sourceKey = source.key,
                            displayName = source.name,
                            mimeType = mimeType,
                            sourceFile = requireNotNull(source.localFile) { "Source file is not available" },
                            origin = origin,
                            modifiedAtMillis = source.modifiedAtMillis,
                            internalTextEditor = internalTextEditor,
                        )
                    }
                }
                val document = if (internalTextEditor) withContext(Dispatchers.IO) {
                    graph.editSessions.readTextDocument(requireNotNull(prepared))
                } else null
                if (_preview.value !== target) {
                    withContext(Dispatchers.IO) { graph.editSessions.discard(prepared) }
                    return@launch
                }
                _fileEditState.value = FileEditUiState(
                    sourceKey = source.key,
                    temporaryDownload = temporaryDownload,
                    session = prepared,
                    text = document?.text,
                    stagedText = document?.text,
                    encoding = document?.encoding,
                    stagedEncoding = document?.encoding,
                    lineEnding = document?.lineEnding,
                    stagedLineEnding = document?.lineEnding,
                    status = if (internalTextEditor) "Paruošta redaguoti" else "Redaguojama kopija paruošta",
                )
                if (source is PreviewSource.Privileged) {
                    val removed = withContext(Dispatchers.IO) { graph.privilegedFiles.discardPreview(source.cachedFile) }
                    if (!removed) message("Laikinos pradinio failo kopijos pašalinti nepavyko", true)
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { graph.editSessions.discard(prepared) }
                throw cancelled
            } catch (error: Throwable) {
                withContext(Dispatchers.IO) { graph.editSessions.discard(prepared) }
                _fileEditState.value = FileEditUiState(
                    sourceKey = source.key,
                    temporaryDownload = temporaryDownload,
                    error = error.message ?: "Nepavyko paruošti redaguojamos kopijos",
                )
            } finally {
                if (fileEditRequestId == requestId) fileEditJob = null
            }
        }
    }

    fun updateEditText(text: String) {
        _fileEditState.update { state ->
            if (state.session?.usesInternalTextEditor != true || state.saving || state.modifyingPdf) state
            else state.copy(
                text = text,
                textChanged = text != state.stagedText,
                status = null,
                error = null,
                conflict = null,
                saveAsConflict = null,
            )
        }
    }

    fun updateEditEncoding(encoding: TextEncoding) {
        _fileEditState.update { state ->
            if (state.session?.usesInternalTextEditor != true || state.saving || state.modifyingPdf) state
            else state.copy(encoding = encoding, status = null, error = null, conflict = null, saveAsConflict = null)
        }
    }

    fun updateEditLineEnding(lineEnding: LineEnding) {
        _fileEditState.update { state ->
            if (state.session?.usesInternalTextEditor != true || state.saving || state.modifyingPdf) state
            else state.copy(lineEnding = lineEnding, status = null, error = null, conflict = null, saveAsConflict = null)
        }
    }

    fun saveFileEdit(forceOverwrite: Boolean = false) {
        val snapshot = _fileEditState.value
        if (snapshot.session == null || snapshot.saving || snapshot.preparing || snapshot.modifyingPdf) return
        val requestId = ++fileEditRequestId
        fileEditJob?.cancel()
        _fileEditState.update { it.copy(saving = true, status = null, error = null, conflict = null, saveAsConflict = null) }
        fileEditJob = viewModelScope.launch {
            try {
                var session = requireNotNull(snapshot.session)
                if (session.usesInternalTextEditor && (snapshot.textChanged || snapshot.formatChanged)) {
                    val text = requireNotNull(snapshot.text) { "Editor content is unavailable" }
                    val encoding = requireNotNull(snapshot.encoding) { "Editor encoding is unavailable" }
                    val lineEnding = requireNotNull(snapshot.lineEnding) { "Editor line ending is unavailable" }
                    session = withContext(Dispatchers.IO) {
                        graph.editSessions.stageText(session, text, encoding, lineEnding)
                    }
                    _fileEditState.update { current ->
                        if (current.session?.id == session.id) {
                            current.copy(
                                session = session,
                                stagedText = text,
                                stagedEncoding = encoding,
                                stagedLineEnding = lineEnding,
                                textChanged = false,
                            )
                        } else current
                    }
                } else {
                    session = withContext(Dispatchers.IO) { graph.editSessions.refreshWorking(session) }
                }

                val result = when (session.origin) {
                    is EditOrigin.Local -> withContext(Dispatchers.IO) {
                        graph.editSessions.saveLocal(session, forceOverwrite).also { result ->
                            if (result is EditSaveResult.Saved) graph.sharedStorageIndex.changed(listOf(File((session.origin as EditOrigin.Local).path)))
                        }
                    }
                    is EditOrigin.Content -> withContext(Dispatchers.IO) {
                        saveContentOrigin(session, forceOverwrite)
                    }
                    is EditOrigin.Privileged -> withContext(Dispatchers.IO) {
                        graph.privilegedFiles.saveOrigin(session, forceOverwrite)
                    }
                }
                when (result) {
                    is EditSaveResult.Conflict -> _fileEditState.update { current ->
                        if (current.session?.id == session.id) {
                            current.copy(
                                session = session,
                                saving = false,
                                conflict = result.details,
                                closeAfterSave = false,
                                status = null,
                                error = null,
                            )
                        } else current
                    }
                    is EditSaveResult.Saved -> {
                        val saved = withContext(Dispatchers.IO) {
                            graph.editSessions.markOriginSaved(session, result.revision)
                        }
                        val completionStatus = result.warning ?: "Išsaugota pradinėje vietoje"
                        var shouldClose = false
                        _fileEditState.update { current ->
                            if (current.session?.id == session.id) {
                                shouldClose = EditSessionLifecycleRules.closeAfterSuccessfulSave(
                                    temporaryDownload = current.temporaryDownload,
                                    closeRequested = current.closeAfterSave,
                                )
                                current.copy(
                                    session = saved,
                                    saving = false,
                                    textChanged = false,
                                    stagedEncoding = current.encoding,
                                    stagedLineEnding = current.lineEnding,
                                    conflict = null,
                                    saveAsConflict = null,
                                    closeAfterSave = false,
                                    status = completionStatus,
                                    error = null,
                                )
                            } else current
                        }
                        if (shouldClose) {
                            message(completionStatus)
                            fileEditJob = null
                            closePreviewImmediately()
                        }
                        when (session.origin) {
                            is EditOrigin.Local -> {
                                refreshPanel()
                            }
                            is EditOrigin.Privileged -> refreshAdvancedBrowser()
                            is EditOrigin.Content -> Unit
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _fileEditState.update {
                    it.copy(saving = false, closeAfterSave = false, error = error.message ?: "Failo išsaugoti nepavyko")
                }
            } finally {
                if (fileEditRequestId == requestId) fileEditJob = null
            }
        }
    }

    fun saveFileEditAs(destination: Uri) {
        val snapshot = _fileEditState.value
        if (snapshot.session == null || snapshot.saving || snapshot.preparing || snapshot.modifyingPdf) return
        val requestId = ++fileEditRequestId
        fileEditJob?.cancel()
        _fileEditState.update { it.copy(saving = true, status = null, error = null, conflict = null, saveAsConflict = null) }
        fileEditJob = viewModelScope.launch {
            try {
                var session = requireNotNull(snapshot.session)
                if (session.usesInternalTextEditor && (snapshot.textChanged || snapshot.formatChanged)) {
                    val text = requireNotNull(snapshot.text) { "Editor content is unavailable" }
                    val encoding = requireNotNull(snapshot.encoding) { "Editor encoding is unavailable" }
                    val lineEnding = requireNotNull(snapshot.lineEnding) { "Editor line ending is unavailable" }
                    session = withContext(Dispatchers.IO) {
                        graph.editSessions.stageText(session, text, encoding, lineEnding)
                    }
                } else {
                    session = withContext(Dispatchers.IO) { graph.editSessions.refreshWorking(session) }
                }
                val verified = withContext(Dispatchers.IO) { writeContentDestination(session, destination) }
                require(session.workingRevision.hasSameContent(verified)) { "Išsaugotos kopijos patikra nepavyko" }
                val saved = withContext(Dispatchers.IO) {
                    graph.editSessions.rebaseOrigin(
                        session = session,
                        destination = EditDestination.Content(
                            destination.toString(),
                            contentDisplayName(destination) ?: session.displayName,
                        ),
                        savedRevision = verified,
                    )
                }
                val completionStatus = "Išsaugota pasirinktoje vietoje"
                var shouldClose = false
                _fileEditState.update { current ->
                    if (current.session?.id == session.id) {
                        shouldClose = EditSessionLifecycleRules.closeAfterSuccessfulSave(
                            temporaryDownload = current.temporaryDownload,
                            closeRequested = current.closeAfterSave,
                        )
                        current.copy(
                            session = saved,
                            saving = false,
                            stagedText = current.text,
                            stagedEncoding = current.encoding,
                            stagedLineEnding = current.lineEnding,
                            textChanged = false,
                            conflict = null,
                            saveAsConflict = null,
                            closeAfterSave = false,
                            status = "Išsaugota pasirinktoje vietoje; tai dabar aktyvus failas",
                            error = null,
                        )
                    } else current
                }
                if (shouldClose) {
                    message(completionStatus)
                    fileEditJob = null
                    closePreviewImmediately()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _fileEditState.update {
                    it.copy(saving = false, closeAfterSave = false, error = error.message ?: "Kopijos išsaugoti nepavyko")
                }
            } finally {
                if (fileEditRequestId == requestId) fileEditJob = null
            }
        }
    }

    fun saveFileEditAsLocal(
        directoryPath: String,
        requestedName: String,
        policy: EditExistingPolicy = EditExistingPolicy.ASK,
    ) {
        val destination = runCatching {
            val name = EditDestinationRules.validateFileName(requestedName)
            EditDestination.Local(File(File(directoryPath).canonicalFile, name).absolutePath)
        }.getOrElse { error ->
            _fileEditState.update { it.copy(error = error.message ?: "Netinkamas failo vardas") }
            return
        }
        saveFileEditAsDestination(destination, policy)
    }

    fun resolveFileEditSaveAsConflict(policy: EditExistingPolicy) {
        require(policy != EditExistingPolicy.ASK) { "Choose replace or keep both" }
        val destination = _fileEditState.value.saveAsConflict?.destination ?: return
        _fileEditState.update { it.copy(saveAsConflict = null) }
        saveFileEditAsDestination(destination, policy)
    }

    fun dismissFileEditSaveAsConflict() {
        _fileEditState.update { it.copy(saveAsConflict = null) }
    }

    private fun saveFileEditAsDestination(destination: EditDestination, policy: EditExistingPolicy) {
        val snapshot = _fileEditState.value
        if (snapshot.session == null || snapshot.saving || snapshot.preparing || snapshot.modifyingPdf) return
        val requestId = ++fileEditRequestId
        fileEditJob?.cancel()
        _fileEditState.update {
            it.copy(saving = true, status = null, error = null, conflict = null, saveAsConflict = null)
        }
        fileEditJob = viewModelScope.launch {
            try {
                var session = requireNotNull(snapshot.session)
                if (session.usesInternalTextEditor && (snapshot.textChanged || snapshot.formatChanged)) {
                    val text = requireNotNull(snapshot.text) { "Editor content is unavailable" }
                    val encoding = requireNotNull(snapshot.encoding) { "Editor encoding is unavailable" }
                    val lineEnding = requireNotNull(snapshot.lineEnding) { "Editor line ending is unavailable" }
                    session = withContext(Dispatchers.IO) {
                        graph.editSessions.stageText(session, text, encoding, lineEnding)
                    }
                } else {
                    session = withContext(Dispatchers.IO) { graph.editSessions.refreshWorking(session) }
                }

                val result = when (destination) {
                    is EditDestination.Local -> withContext(Dispatchers.IO) {
                        val target = File(destination.path)
                        graph.editSessions.saveLocalAs(
                            session = session,
                            directoryPath = requireNotNull(target.parentFile).absolutePath,
                            requestedName = target.name,
                            policy = policy,
                        ).also { saved ->
                            if (saved is EditSaveAsResult.Saved) {
                                val actual = saved.destination as EditDestination.Local
                                graph.sharedStorageIndex.changed(listOf(File(actual.path)))
                            }
                        }
                    }
                    is EditDestination.Content -> error("Content destinations use the Android document picker")
                }

                when (result) {
                    is EditSaveAsResult.Conflict -> _fileEditState.update { current ->
                        if (current.session?.id == session.id) {
                            current.copy(
                                session = session,
                                saving = false,
                                stagedText = current.text,
                                stagedEncoding = current.encoding,
                                stagedLineEnding = current.lineEnding,
                                textChanged = false,
                                saveAsConflict = result.details,
                                closeAfterSave = false,
                            )
                        } else current
                    }
                    is EditSaveAsResult.Saved -> {
                        val saved = withContext(Dispatchers.IO) {
                            graph.editSessions.rebaseOrigin(session, result.destination, result.revision)
                        }
                        val completionStatus = result.warning ?: "Išsaugota kaip ${result.destination.label}"
                        var shouldClose = false
                        _fileEditState.update { current ->
                            if (current.session?.id == session.id) {
                                shouldClose = EditSessionLifecycleRules.closeAfterSuccessfulSave(
                                    temporaryDownload = current.temporaryDownload,
                                    closeRequested = current.closeAfterSave,
                                )
                                current.copy(
                                    session = saved,
                                    saving = false,
                                    stagedText = current.text,
                                    stagedEncoding = current.encoding,
                                    stagedLineEnding = current.lineEnding,
                                    textChanged = false,
                                    conflict = null,
                                    saveAsConflict = null,
                                    closeAfterSave = false,
                                    status = result.warning ?: "Išsaugota kaip ${result.destination.label}; tai dabar aktyvus failas",
                                    error = null,
                                )
                            } else current
                        }
                        when (result.destination) {
                            is EditDestination.Local -> {
                                refreshPanel()
                            }
                            is EditDestination.Content -> Unit
                        }
                        if (shouldClose) {
                            message(completionStatus)
                            fileEditJob = null
                            closePreviewImmediately()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _fileEditState.update {
                    it.copy(saving = false, closeAfterSave = false, error = error.message ?: "Failo išsaugoti nepavyko")
                }
            } finally {
                if (fileEditRequestId == requestId) fileEditJob = null
            }
        }
    }

    fun refreshSignatureLibrary() {
        runSignatureLibraryOperation("Išsaugotų parašų įkelti nepavyko") {
            graph.signatureLibrary.load()
        }
    }

    fun saveSignature(name: String, drawing: SignatureDrawing) {
        runSignatureLibraryOperation("Parašo išsaugoti nepavyko") {
            graph.signatureLibrary.save(name, drawing)
        }
    }

    fun deleteSavedSignature(id: String) {
        runSignatureLibraryOperation("Parašo pašalinti nepavyko") {
            graph.signatureLibrary.delete(id)
        }
    }

    private fun runSignatureLibraryOperation(
        failureMessage: String,
        operation: () -> List<SavedSignature>,
    ) {
        if (signatureLibraryJob?.isActive == true) return
        _signatureLibrary.update { it.copy(loading = true, error = null) }
        signatureLibraryJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { operation() }
                _signatureLibrary.value = SignatureLibraryUiState(items = items)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _signatureLibrary.update { it.copy(loading = false, error = failureMessage) }
            } finally {
                signatureLibraryJob = null
            }
        }
    }

    fun applyPdfVisualSignature(
        drawing: SignatureDrawing,
        placement: PdfSignaturePlacement,
    ) {
        val snapshot = _fileEditState.value
        val session = snapshot.session ?: return
        if (snapshot.saving || snapshot.preparing || snapshot.modifyingPdf) return
        if (!session.displayName.endsWith(".pdf", ignoreCase = true) && session.mimeType != "application/pdf") {
            _fileEditState.update { it.copy(error = "Pasirašyti galima tik PDF failą") }
            return
        }
        val requestId = ++fileEditRequestId
        fileEditJob?.cancel()
        _fileEditState.update {
            it.copy(
                modifyingPdf = true,
                status = "Parašas įrašomas į privačią darbinę kopiją…",
                error = null,
                conflict = null,
                saveAsConflict = null,
            )
        }
        fileEditJob = viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    graph.editSessions.transformWorking(session) { source, destination ->
                        graph.pdfSignatures.apply(source, destination, drawing, placement)
                    }
                }
                _fileEditState.update { current ->
                    if (current.session?.id == session.id) {
                        current.copy(
                            session = refreshed,
                            modifyingPdf = false,
                            status = "Matomas parašas pridėtas. Pasirinkite Išsaugoti arba Išsaugoti kaip.",
                            error = null,
                        )
                    } else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _fileEditState.update { current ->
                    if (current.session?.id == session.id) {
                        current.copy(
                            modifyingPdf = false,
                            status = null,
                            error = error.message ?: "Parašo pridėti nepavyko",
                        )
                    } else current
                }
            } finally {
                if (fileEditRequestId == requestId) fileEditJob = null
            }
        }
    }

    fun refreshFileEditAfterExternalEditor() {
        val snapshot = _fileEditState.value
        val session = snapshot.session ?: return
        if (snapshot.saving || snapshot.preparing || snapshot.modifyingPdf) return
        val requestId = ++fileEditRequestId
        fileEditJob?.cancel()
        _fileEditState.update { it.copy(preparing = true, status = null, error = null) }
        fileEditJob = viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) { graph.editSessions.refreshWorking(session) }
                val refreshedDocument = if (session.usesInternalTextEditor) withContext(Dispatchers.IO) {
                    graph.editSessions.readTextDocument(refreshed)
                } else null
                val refreshedText = refreshedDocument?.text ?: snapshot.text
                _fileEditState.update { current ->
                    if (current.session?.id == session.id) {
                        current.copy(
                            session = refreshed,
                            text = refreshedText,
                            stagedText = refreshedText,
                            encoding = refreshedDocument?.encoding ?: current.encoding,
                            stagedEncoding = refreshedDocument?.encoding ?: current.stagedEncoding,
                            lineEnding = refreshedDocument?.lineEnding ?: current.lineEnding,
                            stagedLineEnding = refreshedDocument?.lineEnding ?: current.stagedLineEnding,
                            textChanged = false,
                            preparing = false,
                            status = if (refreshed.workingRevision.hasSameContent(session.workingRevision)) {
                                "Išorinis redaktorius uždarytas nepakeitus turinio"
                            } else {
                                "Išorinio redaktoriaus pakeitimus galima išsaugoti"
                            },
                            error = null,
                        )
                    } else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _fileEditState.update {
                    it.copy(preparing = false, error = error.message ?: "Redaguotos kopijos perskaityti nepavyko")
                }
            } finally {
                if (fileEditRequestId == requestId) fileEditJob = null
            }
        }
    }

    fun dismissFileEditConflict() {
        _fileEditState.update { it.copy(conflict = null) }
    }

    private fun shouldOpenWithAdvancedAccess(path: String): Boolean {
        if (!graph.advancedAccess.state.value.connected) return false
        val primary = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        return listOf("$primary/Android/data", "$primary/Android/obb").any { protectedRoot ->
            path == protectedRoot || path.startsWith("$protectedRoot/")
        }
    }

    fun clearShovelClipboard() {
        _shovelClipboard.value = null
        _clipboard.value = null
    }

    fun copyArchiveEntryToSet(entryPath: String) {
        val target = _preview.value as? PreviewTarget.Archive ?: run {
            message("Į Shovel planą galima pridėti tik vietinio archyvo įrašą", true)
            return
        }
        val source = runCatching {
            ShovelSourceRef(
                location = ShovelLocationRef.local(target.file.absolutePath),
                displayName = entryPath.trim('/').substringAfterLast('/').ifBlank {
                    target.file.name.substringBeforeLast('.', target.file.name)
                },
                kind = ShovelSourceKind.ARCHIVE_ENTRY,
                archiveEntryPath = entryPath,
            ).normalized()
        }.getOrElse { error ->
            message(error.message ?: "Archyvo įrašo pridėti nepavyko", true)
            return
        }
        val existing = _shovelClipboard.value
        if (existing?.moveAfterVerifiedCopies == true) {
            message("Archyvo įrašų negalima pridėti prie iškirpimo rinkinio", true)
            return
        }
        val merged = ClipboardMergeRules.merge(
            existing = existing?.sources.orEmpty(),
            additional = listOf(source),
            maximum = ShovelWorkflowLimits.MAX_SOURCE_ROOTS,
            key = { item -> "${item.kind}:${item.location.identityKey()}:${item.archiveEntryPath.orEmpty()}" },
        )
        _shovelClipboard.value = ShovelClipboardState(merged.items)
        _clipboard.value = null
        message(if (merged.addedCount > 0) "Archyvo įrašas pridėtas prie kopijavimo rinkinio" else "Archyvo įrašas jau yra kopijavimo rinkinyje")
    }

    fun closePreview() {
        if (_fileEditState.value.modifyingPdf) {
            return
        } else if (_fileEditState.value.saving) {
            _fileEditState.update { it.copy(closeAfterSave = true) }
        } else if (_fileEditState.value.hasUnsavedChanges) {
            _fileEditState.update { it.copy(confirmDiscard = true) }
        } else {
            closePreviewImmediately()
        }
    }

    fun keepEditing() {
        _fileEditState.update { it.copy(confirmDiscard = false) }
    }

    fun discardFileEditAndClose() {
        closePreviewImmediately()
    }

    fun openExternalUri(uri: Uri, mimeType: String?) {
        when (uri.scheme) {
            "file" -> {
                val path = uri.path
                if (path.isNullOrBlank()) {
                    message("Failo kelias nepateiktas", true)
                    return
                }
                runCatching { File(path).canonicalFile }
                    .onSuccess { file ->
                        if (file.isFile && file.canRead()) open(graph.localFiles.toEntry(file))
                        else message("Failas nepasiekiamas", true)
                    }
                    .onFailure { message(it.message ?: "Failas nepasiekiamas", true) }
            }
            "content" -> viewModelScope.launch {
                graph.contentFiles.describe(uri, mimeType).fold(
                    onSuccess = { _preview.value = PreviewTarget.ContentFile(it) },
                    onFailure = { message(it.message ?: "Failo atidaryti nepavyko", true) },
                )
            }
            else -> message("Šio tipo nuoroda nepalaikoma", true)
        }
    }

    fun extractArchive(file: FileEntry, destinationDirectory: String, password: CharArray? = null) {
        val fallbackName = getApplication<Application>().appString(R.string.generated_extracted_name)
        val destinationRoot = runCatching { File(destinationDirectory).canonicalFile }.getOrElse {
            password?.fill('\u0000')
            message(it.message ?: "Paskirties katalogas nepasiekiamas", true)
            return
        }
        if (!destinationRoot.isDirectory || !destinationRoot.canWrite()) {
            password?.fill('\u0000')
            message("Paskirties katalogas neleidžia rašyti", true)
            return
        }
        val destination = FileSystemRules.keepBothTarget(
            File(destinationRoot, ArchiveMutationRules.extractionBaseName(file.name, fallbackName)),
        )
        val staging = File(destinationRoot, ".shovel-extract-${UUID.randomUUID()}")
        graph.operationManager.submit("Išpakuojamas ${file.name}") {
            try {
                graph.archives.extract(
                    archiveFile = file.file,
                    destinationDirectory = staging,
                    password = password,
                    operation = this,
                    fallbackExtractedName = fallbackName,
                )
                require(!destination.exists() && staging.renameTo(destination)) { "Išpakavimo užbaigti nepavyko" }
                message("Archyvas išpakuotas į ${destination.absolutePath}")
                closePreview()
                refreshPanel()
            } finally {
                password?.fill('\u0000')
                if (staging.exists()) staging.deleteRecursively()
            }
        }.onFailure {
            password?.fill('\u0000')
            message(it.message ?: "Išpakavimo pradėti nepavyko", true)
        }
    }

    fun createArchive(
        name: String,
        format: ArchiveFormat,
        password: CharArray? = null,
        sourcePaths: Collection<String>? = null,
        compressionLevel: Int = ArchiveCompressionRules.DEFAULT_LEVEL,
        maxOutputBytes: Long? = null,
        hideNames: Boolean = false,
    ) {
        val state = _panel.value
        val createEmpty = sourcePaths != null && sourcePaths.isEmpty()
        val requestedPaths = (sourcePaths ?: state.selectedPaths).distinct()
        if (requestedPaths.isEmpty() && !createEmpty) {
            password?.fill('\u0000')
            message("Nepasirinkta failų", true)
            return
        }
        if (createEmpty && password != null) {
            password.fill('\u0000')
            message("Tuščias archyvas negali būti užšifruotas", true)
            return
        }
        val availableByPath = state.entries.associateBy(FileEntry::absolutePath)
        val selectedEntries = requestedPaths.mapNotNull(availableByPath::get)
        if (selectedEntries.size != requestedPaths.size || selectedEntries.any { !it.isReadable || !it.file.exists() }) {
            password?.fill('\u0000')
            message("Kai kurie pasirinkti failai nebepasiekiami", true)
            return
        }
        val validatedName = FileSystemRules.validateFileName(name).getOrElse { error ->
            password?.fill('\u0000')
            message(error.message ?: "Netinkamas archyvo pavadinimas", true)
            return
        }
        val suffix = when (format) {
            ArchiveFormat.ZIP -> ".zip"
            ArchiveFormat.SEVEN_Z -> ".7z"
            ArchiveFormat.TAR -> ".tar"
            ArchiveFormat.TAR_GZ -> ".tar.gz"
            else -> ""
        }
        val fileName = if (validatedName.lowercase().endsWith(suffix)) validatedName else validatedName + suffix
        FileSystemRules.validateFileName(fileName).getOrElse { error ->
            password?.fill('\u0000')
            message(error.message ?: "Netinkamas archyvo pavadinimas", true)
            return
        }
        val directory = runCatching { File(state.path).canonicalFile }.getOrElse { error ->
            password?.fill('\u0000')
            message(error.message ?: "Aplankas nepasiekiamas", true)
            return
        }
        val requestedOutput = runCatching { File(directory, fileName).canonicalFile }.getOrElse { error ->
            password?.fill('\u0000')
            message(error.message ?: "Netinkamas archyvo pavadinimas", true)
            return
        }
        if (requestedOutput.parentFile != directory || !FileSystemRules.isContained(directory, requestedOutput)) {
            password?.fill('\u0000')
            message("Netinkamas archyvo pavadinimas", true)
            return
        }
        val output = FileSystemRules.keepBothTarget(requestedOutput)
        val sources = selectedEntries.map(FileEntry::file)
        val validatedCompressionLevel = runCatching { ArchiveCompressionRules.validated(compressionLevel) }.getOrElse { error ->
            password?.fill('\u0000')
            message(error.message ?: "Netinkamas suspaudimo lygis", true)
            return
        }
        graph.operationManager.submit("Kuriamas ${output.name}") {
            graph.archives.create(
                format = format,
                outputFile = output,
                sources = sources,
                password = password,
                operation = this,
                compressionLevel = validatedCompressionLevel,
                maxOutputBytes = maxOutputBytes,
                hideNames = hideNames,
            )
        }.onSuccess { clearSelection() }
            .onFailure {
                password?.fill('\u0000')
                message(it.message ?: "Archyvo kūrimo pradėti nepavyko", true)
            }
    }

    suspend fun loadFileSelectionInfo(paths: Collection<String>): Result<FileSelectionSummary> = runCatching {
        graph.fileSelectionInfo.scan(paths)
    }

    fun search(filters: SearchFilters, roots: List<String> = listOf(panelState().path)) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = SearchUiState(filters = filters, roots = roots, running = true)
            try {
                val pathPredicate: (FileEntry) -> Boolean = { true }
                val result = graph.fileCategories.searchIndexed(roots, filters, pathPredicate)
                    ?: graph.search.search(roots, filters, pathPredicate)
                _searchState.value = SearchUiState(
                    filters = filters,
                    roots = roots,
                    results = result.entries,
                    scannedEntries = result.scannedEntries,
                    truncated = result.truncated,
                    running = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _searchState.value = SearchUiState(filters = filters, roots = roots, running = false, error = error.message)
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        _searchState.update { it.copy(running = false, error = null) }
    }

    fun toggleSearchSelection(path: String) {
        _searchState.update { state ->
            val selection = state.selectedPaths.toMutableSet()
            if (!selection.add(path)) selection.remove(path)
            state.copy(selectedPaths = selection)
        }
    }

    fun clearSearchSelection() {
        _searchState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun selectAllSearchResults() {
        _searchState.update { it.copy(selectedPaths = it.results.map(FileEntry::absolutePath).toSet()) }
    }

    fun copySearchSelection(move: Boolean) {
        val selected = orderedSearchSelection()
        if (selected.isEmpty()) return
        if (setLocalClipboard(selected, move = move, append = false)) clearSearchSelection()
    }

    fun addSearchSelectionToClipboard() {
        val selected = orderedSearchSelection()
        if (selected.isEmpty()) return
        if (setLocalClipboard(selected, move = false, append = true)) clearSearchSelection()
    }

    fun trashSearchSelection() {
        val selected = orderedSearchSelection()
        if (selected.isEmpty()) return
        graph.operationManager.submit("Keliama į šiukšlinę") {
            graph.trash.moveToTrash(selected, this)
            clearSearchSelection()
            rerunSearchIfVisible()
        }.onFailure { message(it.message ?: "Operacijos pradėti nepavyko", true) }
    }

    fun trashDuplicateCopies(paths: List<String>) {
        val selected = paths.distinct()
        if (selected.isEmpty()) return
        if (selected.size > 10_000) {
            message("Vienu metu galima tvarkyti iki 10 000 dublikatų", true)
            return
        }
        val analysisSnapshot = _analysisState.value
        graph.operationManager.submit("Dublikatų kopijos keliamos į šiukšlinę") {
            graph.trash.moveToTrash(selected, this)
            viewModelScope.launch { reanalyze(analysisSnapshot) }
        }.onFailure { message(it.message ?: "Dublikatų tvarkymo pradėti nepavyko", true) }
    }

    fun batchRenameSearchSelection() {
        beginBatchRename(orderedSearchSelection())
    }

    fun revealSearchResult(entry: FileEntry) {
        val parent = entry.file.parentFile ?: return
        _section.value = AppSection.FILES
        navigate(parent.absolutePath)
        _panel.update { it.copy(selectedPaths = setOf(entry.absolutePath)) }
    }

    fun analyze(path: String = panelState().path) {
        startAnalysis(listOf(path), allStorage = false)
    }

    fun refreshAnalysis() {
        val state = _analysisState.value
        if (state.rootPaths.isEmpty() && state.rootPath == null) analyze() else reanalyze(state)
    }

    fun analyzeEntries(paths: Collection<String>) {
        val limit = com.shovel.filemanager.search.FileSearchEngine.MAX_ANALYSIS_ROOTS
        val requested = paths.asSequence()
            .mapNotNull { path -> runCatching { File(path).canonicalFile }.getOrNull() }
            .filter(File::exists)
            .distinctBy(File::getAbsolutePath)
            .sortedBy { it.absolutePath.length }
            .toList()
        val compact = requested.filter { candidate ->
            requested.none { parent ->
                parent != candidate && FileSystemRules.isContained(parent, candidate)
            }
        }
        if (compact.isEmpty()) {
            message("Nepasirinkta paieškos vieta", true)
            return
        }
        if (compact.size > limit) {
            message("Vienu metu galima analizuoti iki $limit vietų", true)
        }
        _cleanupRequested.value = false
        _section.value = AppSection.ANALYZE
        startAnalysis(compact.take(limit).map(File::getAbsolutePath), allStorage = false)
    }

    fun analyzeAllStorage() {
        val paths = _roots.value.map(StorageRoot::path)
        if (paths.isEmpty()) {
            message("Nepasirinkta paieškos vieta", true)
            return
        }
        startAnalysis(paths, allStorage = true)
    }

    private fun startAnalysis(paths: List<String>, allStorage: Boolean) {
        val normalizedPaths = paths.asSequence()
            .mapNotNull { path -> runCatching { File(path).canonicalPath }.getOrNull() }
            .distinct()
            .take(com.shovel.filemanager.search.FileSearchEngine.MAX_ANALYSIS_ROOTS)
            .toList()
        if (normalizedPaths.isEmpty()) {
            message("Analizės vieta nebepasiekiama", true)
            return
        }
        val requestId = ++analysisRequestId
        analysisJob?.cancel()
        similarImagesJob?.cancel()
        val singleRoot = normalizedPaths.singleOrNull().takeUnless { allStorage }
        _analysisState.value = AnalysisUiState(
            rootPath = singleRoot,
            rootPaths = normalizedPaths,
            allStorage = allStorage,
            running = true,
            cancelled = false,
        )
        analysisJob = viewModelScope.launch {
            try {
                val analysis = graph.search.analyze(normalizedPaths) { progress ->
                    if (analysisRequestId == requestId) {
                        _analysisState.update { current ->
                            if (current.running && current.rootPaths == normalizedPaths) current.copy(progress = progress) else current
                        }
                    }
                }
                if (analysisRequestId != requestId) return@launch
                _analysisState.update { current -> current.copy(analysis = analysis) }
                val duplicateAnalysis = graph.search.duplicates(normalizedPaths) { progress ->
                    if (analysisRequestId == requestId) {
                        _analysisState.update { current ->
                            if (current.running && current.rootPaths == normalizedPaths) current.copy(progress = progress) else current
                        }
                    }
                }
                if (analysisRequestId != requestId) return@launch
                _analysisState.value = AnalysisUiState(
                    analysis = analysis,
                    duplicates = duplicateAnalysis.groups,
                    duplicateCandidatesScanned = duplicateAnalysis.scannedCandidates,
                    duplicateScanTruncated = duplicateAnalysis.truncated,
                    rootPath = singleRoot,
                    rootPaths = normalizedPaths,
                    allStorage = allStorage,
                    running = false,
                    progress = null,
                )
            } catch (cancelled: CancellationException) {
                if (analysisRequestId == requestId) {
                    _analysisState.update { it.copy(running = false, progress = null, cancelled = true) }
                }
                throw cancelled
            } catch (error: Throwable) {
                if (analysisRequestId != requestId) return@launch
                _analysisState.value = AnalysisUiState(
                    rootPath = singleRoot,
                    rootPaths = normalizedPaths,
                    allStorage = allStorage,
                    running = false,
                    progress = null,
                    error = error.message,
                )
            } finally {
                if (analysisRequestId == requestId) analysisJob = null
            }
        }
    }

    fun canDeletePreview(target: PreviewTarget): Boolean = when (target) {
        is PreviewTarget.ArchiveEntry -> false
        else -> true
    }

    fun deletePreviewTarget() {
        val target = _preview.value ?: return
        if (!canDeletePreview(target)) return
        if (_fileEditState.value.hasUnsavedChanges || _fileEditState.value.saving || _fileEditState.value.modifyingPdf) {
            message("Pirmiausia išsaugokite arba atmeskite redagavimo pakeitimus", true)
            return
        }

        val title = when (target) {
            is PreviewTarget.LocalFile -> "Keliama į šiukšlinę"
            is PreviewTarget.Archive -> "Keliama į šiukšlinę"
            is PreviewTarget.TrashFile -> "Šalinama iš šiukšliadėžės"
            is PreviewTarget.ContentFile -> "Šalinamas Android dokumentas"
            is PreviewTarget.PrivilegedFile -> if (target.localOrigin) "Keliama į šiukšlinę" else "Šalinamas apsaugotas failas"
            is PreviewTarget.ArchiveEntry -> return
        }
        graph.operationManager.submit(title) {
            when (target) {
                is PreviewTarget.LocalFile -> graph.trash.moveToTrash(listOf(target.entry.absolutePath), this)
                is PreviewTarget.Archive -> graph.trash.moveToTrash(listOf(target.file.absolutePath), this)
                is PreviewTarget.TrashFile -> graph.trash.deleteForever(target.itemId, this).getOrThrow()
                is PreviewTarget.ContentFile -> {
                    setTotals(1, target.entry.sizeBytes)
                    checkpoint()
                    graph.contentFiles.delete(target.entry.uri).getOrThrow()
                    progress(itemDelta = 1, byteDelta = target.entry.sizeBytes ?: 0L, currentName = target.entry.name)
                }
                is PreviewTarget.PrivilegedFile -> if (target.localOrigin) {
                    graph.trash.moveToTrash(listOf(target.entry.absolutePath), this)
                } else graph.privilegedFiles.deletePermanently(listOf(target.entry.absolutePath), this)
                is PreviewTarget.ArchiveEntry -> return@submit
            }
            if (_preview.value == target) closePreviewImmediately()
            when (target) {
                is PreviewTarget.PrivilegedFile -> if (!target.localOrigin) refreshAdvancedBrowser() else {
                    refreshPanel()
                    refreshFileCategory()
                    refreshRecentFiles()
                    refreshTrash()
                }
                else -> {
                    refreshPanel()
                    refreshFileCategory()
                    refreshRecentFiles()
                    refreshTrash()
                }
            }
        }.onFailure { message(it.message ?: "Trynimo pradėti nepavyko", true) }
    }

    fun cancelAnalysis() {
        if (_analysisState.value.running.not()) return
        analysisRequestId += 1L
        analysisJob?.cancel()
        analysisJob = null
        _analysisState.update { it.copy(running = false, progress = null, cancelled = true) }
    }

    private fun reanalyze(state: AnalysisUiState) {
        val current = _analysisState.value
        if (current.rootPaths != state.rootPaths || current.allStorage != state.allStorage) return
        val paths = state.rootPaths.ifEmpty { listOfNotNull(state.rootPath) }
        if (paths.isNotEmpty()) startAnalysis(paths, state.allStorage)
    }

    suspend fun loadCleanupFolder(path: String): Result<DirectoryContentsUsage> {
        val state = _analysisState.value
        val rootPaths = state.rootPaths.ifEmpty { listOfNotNull(state.rootPath) }
        if (rootPaths.isEmpty()) return Result.failure(IllegalStateException("Analizės vieta nebepasiekiama"))
        return try {
            Result.success(graph.search.directoryContentsWithUsage(rootPaths, path))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    fun analyzeSimilarImages() {
        val analysis = _analysisState.value.analysis ?: return
        if (_analysisState.value.similarImagesRunning) return
        similarImagesJob?.cancel()
        similarImagesJob = viewModelScope.launch {
            _analysisState.update {
                it.copy(similarImagesRunning = true, similarImagesAnalyzed = false, similarImagesError = null)
            }
            try {
                val groups = graph.similarImages.find(analysis.similarImageCandidates)
                _analysisState.update {
                    it.copy(
                        similarImages = groups,
                        similarImagesRunning = false,
                        similarImagesAnalyzed = true,
                        similarImagesError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _analysisState.update {
                    it.copy(
                        similarImagesRunning = false,
                        similarImagesAnalyzed = true,
                        similarImagesError = error.message ?: "Panašių nuotraukų analizė nepavyko",
                    )
                }
            }
        }
    }

    fun trashAnalysisSelection(paths: Collection<String>, keepOneCopy: Boolean = true) {
        val state = _analysisState.value
        val rootPaths = state.rootPaths.ifEmpty { listOfNotNull(state.rootPath) }
        val roots = runCatching { rootPaths.map { File(it).canonicalFile }.distinctBy(File::getAbsolutePath) }.getOrElse {
            message("Analizės vieta nebepasiekiama", true)
            return
        }
        if (roots.isEmpty()) return
        val selected = runCatching {
            val requested = paths.distinct()
            require(requested.size <= 10_000) { "Vienu metu galima tvarkyti iki 10 000 elementų" }
            requested.asSequence()
                .map(::File)
                .map { it.canonicalFile }
                .filter(File::exists)
                .onEach { candidate ->
                    require(AnalysisSelectionRules.canModify(roots, candidate)) {
                        "Pasirinktas failas yra už analizuojamo aplanko ribų"
                    }
                }
                .sortedBy { it.absolutePath.length }
                .fold(mutableListOf<File>()) { accepted, candidate ->
                    if (accepted.none { parent -> FileSystemRules.isContained(parent, candidate) }) accepted += candidate
                    accepted
                }
                .toList()
        }.getOrElse {
            message(it.message ?: "Valymo pasirinkimas netinkamas", true)
            return
        }
        if (selected.isEmpty()) return
        val protected = com.shovel.filemanager.operations.CleanupCopyProtection.select(selected,
            state.duplicates.map { it.paths } + state.similarImages.map { group -> group.files.map { it.absolutePath } },
            keepOneCopy = keepOneCopy)
        val retainedRoots = protected.retained
        val movable = protected.movable
        if (movable.isEmpty()) {
            message("Iš kiekvienos vienodų ar panašių failų grupės palikite bent vieną failą", true)
            return
        }
        graph.operationManager.submit("Pasirinkti failai keliami į šiukšlinę") {
            if (retainedRoots.isNotEmpty()) note("Iš kiekvienos vienodų ar panašių failų grupės palikite bent vieną failą")
            graph.trash.moveToTrash(movable.map(File::getAbsolutePath), this)
            if (retainedRoots.isNotEmpty()) message("Iš kiekvienos vienodų ar panašių failų grupės palikite bent vieną failą")
            val removedPaths = movable.asSequence()
                .filterNot(File::exists)
                .map(File::getAbsolutePath)
                .toList()
            if (removedPaths.isNotEmpty()) {
                _analysisState.update { current -> AnalysisResultPruner.prune(current, removedPaths) }
            }
        }.onFailure { message(it.message ?: "Valymo pradėti nepavyko", true) }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            _trashItems.value = graph.trash.list()
            if (_trashBrowser.value.open) refreshTrashBrowser()
        }
    }

    fun openTrashBrowser() {
        val identity = trashDirectoryIdentity(null, "")
        val display = savedDirectoryDisplaySettings(identity)
        val sort = savedDirectorySortSettings(identity)
        _trashBrowser.value = TrashBrowserUiState(
            open = true,
            sortMode = sort.mode,
            sortDirection = sort.direction,
            retentionPeriod = graph.trashRetentionSettings.load(),
        ).withTrashDisplaySettings(display)
        refreshTrashBrowser()
    }

    fun closeTrashBrowser() {
        _trashBrowser.value = TrashBrowserUiState()
    }

    fun refreshTrashBrowser() {
        val snapshot = _trashBrowser.value
        if (!snapshot.open) return
        _trashBrowser.update { current ->
            if (current.itemId == snapshot.itemId && current.relativePath == snapshot.relativePath) {
                current.copy(loading = true, error = null)
            } else current
        }
        viewModelScope.launch {
            runCatching {
                graph.trash.browse(snapshot.itemId, snapshot.relativePath).getOrThrow() to graph.trash.storageState()
            }.fold(
                onSuccess = { (entries, storage) ->
                    _trashBrowser.update { current ->
                        if (current.itemId == snapshot.itemId && current.relativePath == snapshot.relativePath) {
                            current.copy(
                                entries = entries,
                                storedItemCount = storage.storedItemCount,
                                storedItemCountComplete = storage.countComplete,
                                loading = false,
                                error = null,
                            )
                        } else current
                    }
                },
                onFailure = { error ->
                    _trashBrowser.update { current ->
                        if (current.itemId == snapshot.itemId && current.relativePath == snapshot.relativePath) {
                            current.copy(entries = emptyList(), loading = false, error = error.message)
                        } else current
                    }
                },
            )
        }
    }

    fun openTrashEntry(entry: TrashBrowserEntry) {
        if (!entry.isDirectory) {
            _preview.value = PreviewTarget.TrashFile(entry.itemId, entry.toFileEntry())
            return
        }
        val current = _trashBrowser.value
        val nextItemId = current.itemId ?: entry.itemId
        val nextRelativePath = if (current.itemId == null) "" else entry.relativePath
        val nextRootName = current.rootName ?: entry.name
        val identity = trashDirectoryIdentity(nextItemId, nextRelativePath)
        val display = savedDirectoryDisplaySettings(identity)
        val sort = savedDirectorySortSettings(identity)
        _trashBrowser.update {
            it.copy(
                itemId = nextItemId,
                relativePath = nextRelativePath,
                rootName = nextRootName,
                entries = emptyList(),
                error = null,
            ).withTrashDisplaySettings(display)
                .withTrashSortSettings(sort)
        }
        refreshTrashBrowser()
    }

    fun navigateTrashBack(): Boolean {
        val current = _trashBrowser.value
        if (!current.open) return false
        if (current.itemId == null) {
            closeTrashBrowser()
            return true
        }
        val nextItemId = if (current.relativePath.isEmpty()) null else current.itemId
        val nextRelativePath = if (current.relativePath.isEmpty()) "" else TrashPathRules.parent(current.relativePath)
        val identity = trashDirectoryIdentity(nextItemId, nextRelativePath)
        val display = savedDirectoryDisplaySettings(identity)
        val sort = savedDirectorySortSettings(identity)
        _trashBrowser.update {
            it.copy(
                itemId = nextItemId,
                relativePath = nextRelativePath,
                rootName = if (nextItemId == null) null else it.rootName,
                entries = emptyList(),
                error = null,
            ).withTrashDisplaySettings(display)
                .withTrashSortSettings(sort)
        }
        refreshTrashBrowser()
        return true
    }

    fun toggleTrashThumbnails() {
        val snapshot = _trashBrowser.value
        if (!snapshot.open) return
        setTrashDisplaySettings(snapshot.trashDisplaySettings().copy(showThumbnails = !snapshot.showThumbnails))
    }

    fun toggleTrashLayout() {
        val snapshot = _trashBrowser.value
        if (!snapshot.open) return
        setTrashDisplaySettings(
            snapshot.trashDisplaySettings().copy(
                layoutMode = if (snapshot.grid) DirectoryLayoutMode.LIST else DirectoryLayoutMode.GRID,
            ),
        )
    }

    fun setTrashDisplaySettings(settings: DirectoryDisplaySettings) {
        val snapshot = _trashBrowser.value
        if (!snapshot.open) return
        val identity = trashDirectoryIdentity(snapshot.itemId, snapshot.relativePath)
        runCatching { graph.navigation.setDirectoryDisplaySettings(identity, settings) }
            .onSuccess {
                _trashBrowser.update { current ->
                    if (current.itemId == snapshot.itemId && current.relativePath == snapshot.relativePath) {
                        current.withTrashDisplaySettings(settings)
                    } else current
                }
            }
            .onFailure { message(it.message ?: "Šiukšliadėžės vaizdo nustatymo išsaugoti nepavyko", true) }
    }

    fun setTrashSort(mode: SortMode, direction: SortDirection) {
        val snapshot = _trashBrowser.value
        if (!snapshot.open) return
        val identity = trashDirectoryIdentity(snapshot.itemId, snapshot.relativePath)
        runCatching { graph.navigation.setDirectorySortSettings(identity, DirectorySortSettings(mode, direction)) }
            .onSuccess { _trashBrowser.update { it.copy(sortMode = mode, sortDirection = direction) } }
            .onFailure { message(it.message ?: "Šiukšliadėžės rūšiavimo nustatymo išsaugoti nepavyko", true) }
    }

    fun emptyTrash() {
        if (_trashBrowser.value.emptying) return
        _trashBrowser.update { it.copy(emptying = true, error = null) }
        graph.operationManager.submit("Išvaloma šiukšliadėžė") {
            try {
                val result = graph.trash.emptyAll(this)
                _trashBrowser.update {
                    it.copy(
                        itemId = null,
                        relativePath = "",
                        rootName = null,
                        entries = emptyList(),
                        storedItemCount = if (result.failedItems == 0) 0 else it.storedItemCount,
                        storedItemCountComplete = result.failedItems == 0,
                    ).withTrashDisplaySettings(savedDirectoryDisplaySettings(trashDirectoryIdentity(null, "")))
                }
                when {
                    result.failedItems > 0 -> {
                        val summary = "Ištrinta: ${result.deletedItems}, nepavyko ištrinti: ${result.failedItems}"
                        completeWithErrors(result.failedItems, summary)
                        message(summary, true)
                    }
                    result.deletedItems > 0 -> message("Šiukšliadėžė išvalyta: ${result.deletedItems}")
                    else -> message("Šiukšliadėžė jau tuščia")
                }
            } finally {
                _trashBrowser.update { current -> current.copy(emptying = false) }
                refreshTrash()
            }
        }.onFailure {
            _trashBrowser.update { current -> current.copy(emptying = false) }
            message(it.message ?: "Trynimo pradėti nepavyko", true)
        }
    }

    fun refreshNavigationState() {
        runCatching {
            _recents.value = graph.navigation.recents()
            _savedSearches.value = graph.navigation.savedSearches()
            _homeCustomization.value = graph.navigation.homeCustomization(homeBuiltInShortcuts)
        }.onFailure { message(it.message ?: "Naršymo nustatymų perskaityti nepavyko", true) }
    }

    fun setTrashRetentionPeriod(period: TrashRetentionPeriod) {
        runCatching {
            graph.trashRetentionSettings.save(period)
            graph.trashRetentionScheduler.synchronize(period)
        }.onFailure {
            message(it.message ?: "Šiukšliadėžės saugojimo laikotarpio išsaugoti nepavyko", true)
            return
        }
        _trashBrowser.update { it.copy(retentionPeriod = period) }
        if (period == TrashRetentionPeriod.NEVER) return
        graph.operationManager.submit("Valomi pasenę šiukšliadėžės elementai") {
            val result = graph.trash.deleteExpired(period)
            if (result.failedItems > 0) {
                if (result.deletedItems == 0) error("Pasenusių elementų ištrinti nepavyko")
                completeWithErrors(result.failedItems, "Kai kurių pasenusių elementų ištrinti nepavyko")
            }
            refreshTrash()
        }.onFailure { message(it.message ?: "Automatinio šiukšliadėžės valymo pradėti nepavyko", true) }
    }

    fun copyArchiveEntriesToSet(entryPaths: Set<String>) {
        val target = _preview.value as? PreviewTarget.Archive ?: run {
            message("Į kopijavimo rinkinį galima pridėti tik vietinio archyvo įrašus", true)
            return
        }
        val sources = runCatching {
            ArchiveMutationRules.normalizeSelection(entryPaths).map { entryPath ->
                ShovelSourceRef(
                    location = ShovelLocationRef.local(target.file.absolutePath),
                    displayName = entryPath.substringAfterLast('/'),
                    kind = ShovelSourceKind.ARCHIVE_ENTRY,
                    archiveEntryPath = entryPath,
                ).normalized()
            }
        }.getOrElse { error ->
            message(error.message ?: "Archyvo įrašų pridėti nepavyko", true)
            return
        }
        val existing = _shovelClipboard.value
        if (existing?.moveAfterVerifiedCopies == true) {
            message("Archyvo įrašų negalima pridėti prie iškirpimo rinkinio", true)
            return
        }
        val merged = ClipboardMergeRules.merge(
            existing = existing?.sources.orEmpty(),
            additional = sources,
            maximum = ShovelWorkflowLimits.MAX_SOURCE_ROOTS,
            key = { item -> "${item.kind}:${item.location.identityKey()}:${item.archiveEntryPath.orEmpty()}" },
        )
        _shovelClipboard.value = ShovelClipboardState(merged.items)
        _clipboard.value = null
        message(if (merged.addedCount > 0) "Archyvo įrašai pridėti prie kopijavimo rinkinio: ${merged.addedCount}" else "Pasirinkti archyvo įrašai jau yra kopijavimo rinkinyje")
    }

    private val _archivePasswordRequest = MutableStateFlow<FileEntry?>(null)

    /** Set while a password protected archive waits for its password before it can be opened. */
    val archivePasswordRequest: StateFlow<FileEntry?> = _archivePasswordRequest.asStateFlow()

    // The password of the archive that is open in the preview. It stays in memory only until the preview closes.
    private var archiveSession: Pair<String, CharArray>? = null

    private fun archivePasswordFor(archive: File): CharArray? =
        archiveSession?.takeIf { it.first == archive.absolutePath }?.second

    private fun clearArchiveSession() {
        archiveSession?.second?.fill('\u0000')
        archiveSession = null
    }

    fun submitArchivePassword(password: CharArray) {
        val entry = _archivePasswordRequest.value
        if (entry == null) {
            password.fill('\u0000')
            return
        }
        viewModelScope.launch {
            runCatching { graph.archives.list(entry.file, password) }
                .onSuccess { entries ->
                    clearArchiveSession()
                    archiveSession = entry.file.absolutePath to password
                    _archivePasswordRequest.value = null
                    _preview.value = PreviewTarget.Archive(entry, entries)
                }
                .onFailure { error ->
                    password.fill('\u0000')
                    message(error.message ?: "Archyvo perskaityti nepavyko", true)
                }
        }
    }

    fun dismissArchivePasswordRequest() {
        _archivePasswordRequest.value = null
    }

    fun refreshArchivePreview() {
        val target = _preview.value ?: return
        val archiveFile = target.archiveFile() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { graph.archives.list(archiveFile, archivePasswordFor(archiveFile)) }
                .onSuccess { entries -> updateArchivePreviewEntries(target, entries) }
                .onFailure { message(it.message ?: "Archyvo perskaityti nepavyko", true) }
        }
    }

    suspend fun materializeArchiveThumbnail(entryPath: String): Result<File> =
        materializeArchiveEntry(entryPath, MAX_ARCHIVE_THUMBNAIL_BYTES)

    fun openArchiveEntry(entryPath: String) {
        val returnTarget = _preview.value
        if (returnTarget.archiveFile() == null) return
        viewModelScope.launch {
            materializeArchiveEntry(entryPath, MAX_ARCHIVE_PREVIEW_BYTES).fold(
                onSuccess = { file ->
                    if (_preview.value != returnTarget) return@fold
                    archiveReturnTarget = returnTarget
                    _preview.value = PreviewTarget.ArchiveEntry(
                        graph.localFiles.toEntry(file).copy(name = entryPath.replace('\\', '/').substringAfterLast('/')),
                    )
                },
                onFailure = { message(it.message ?: "Archyvo įrašo atidaryti nepavyko", true) },
            )
        }
    }

    fun shareArchiveEntries(entryPaths: Set<String>) {
        val target = _preview.value
        val archive = target.archiveFile() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val stage = File(getApplication<Application>().cacheDir, "archive-share-${UUID.randomUUID()}")
            runCatching {
                graph.archives.extractEntries(archive, entryPaths, stage, null, archivePasswordFor(archive))
                val files = stage.walkTopDown().filter(File::isFile).take(MAX_ARCHIVE_SHARE_FILES + 1).toList()
                require(files.isNotEmpty()) { "Pasirinktuose archyvo įrašuose nėra failų" }
                require(files.size <= MAX_ARCHIVE_SHARE_FILES) { "Pasirinkta per daug archyvo failų bendrinimui" }
                graph.localShare.sharePrepared(files).getOrThrow()
            }.onFailure { message(it.message ?: "Archyvo įrašų bendrinti nepavyko", true) }
            stage.deleteRecursively()
        }
    }

    fun extractArchiveEntries(entryPaths: Set<String>, destinationDirectory: String, password: CharArray? = null) {
        val target = _preview.value
        val archive = target.archiveFile() ?: return
        val destinationParent = runCatching { File(destinationDirectory).canonicalFile }.getOrElse {
            password?.fill('\u0000')
            message(it.message ?: "Paskirties katalogas nepasiekiamas", true)
            return
        }
        if (!destinationParent.isDirectory || !destinationParent.canWrite()) {
            password?.fill('\u0000')
            message("Paskirties katalogas neleidžia rašyti", true)
            return
        }
        val archiveBaseName = ArchiveMutationRules.extractionBaseName(archive.name, "extracted")
        val destination = FileSystemRules.keepBothTarget(File(destinationParent, archiveBaseName))
        val staging = File(destinationParent, ".shovel-extract-${UUID.randomUUID()}")
        graph.operationManager.submit("Išpakuojami pasirinkti archyvo įrašai") {
            try {
                graph.archives.extractEntries(archive, entryPaths, staging, this, password ?: archivePasswordFor(archive))
                require(!destination.exists() && staging.renameTo(destination)) { "Išpakavimo užbaigti nepavyko" }
                message("Pasirinkti archyvo įrašai išpakuoti į ${destination.absolutePath}")
                refreshPanel()
            } finally {
                password?.fill('\u0000')
                if (staging.exists()) staging.deleteRecursively()
            }
        }.onFailure {
            password?.fill('\u0000')
            message(it.message ?: "Išpakavimo pradėti nepavyko", true)
        }
    }

    fun renameArchiveEntry(entryPath: String, newName: String) {
        val target = _preview.value as? PreviewTarget.Archive ?: run {
            message("Nuotolinio archyvo tiesiogiai pakeisti negalima", true)
            return
        }
        graph.operationManager.submit("Pervadinamas archyvo įrašas") {
            runCatching { graph.archives.renameZipEntry(target.file.file, entryPath, newName) }
                .onSuccess { entries -> updateArchivePreviewEntries(target, entries) }
                .onFailure { error -> message(error.message ?: "Archyvo įrašo pervadinti nepavyko", true); throw error }
        }.onFailure { message(it.message ?: "Pervadinimo pradėti nepavyko", true) }
    }

    fun deleteArchiveEntries(entryPaths: Set<String>) {
        val target = _preview.value as? PreviewTarget.Archive ?: run {
            message("Nuotolinio archyvo tiesiogiai pakeisti negalima", true)
            return
        }
        graph.operationManager.submit("Šalinami archyvo įrašai") {
            runCatching { graph.archives.deleteZipEntries(target.file.file, entryPaths) }
                .onSuccess { entries -> updateArchivePreviewEntries(target, entries) }
                .onFailure { error -> message(error.message ?: "Archyvo įrašų pašalinti nepavyko", true); throw error }
        }.onFailure { message(it.message ?: "Šalinimo pradėti nepavyko", true) }
    }

    fun moveArchiveEntriesOut(entryPaths: Set<String>) {
        val target = _preview.value as? PreviewTarget.Archive ?: run {
            message("Nuotolinio archyvo tiesiogiai pakeisti negalima", true)
            return
        }
        val archive = target.file.file
        val destination = FileSystemRules.keepBothTarget(File(archive.parentFile, "${archive.name.substringBeforeLast('.')} - moved"))
        graph.operationManager.submit("Perkeliami archyvo įrašai") {
            graph.archives.extractEntries(archive, entryPaths, destination, this)
            val entries = graph.archives.deleteZipEntries(archive, entryPaths)
            updateArchivePreviewEntries(target, entries)
            refreshPanel()
            message("Archyvo įrašai perkelti į ${destination.name}")
        }.onFailure { message(it.message ?: "Perkėlimo pradėti nepavyko", true) }
    }

    fun shareFileCategorySelection() {
        val state = _fileCategory.value
        val selected = state.entries.filter { it.absolutePath in state.selectedPaths }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val result = if (state.category == FileCategory.INSTALLED_APPS) {
                val temporaryRoot = File(getApplication<Application>().cacheDir, "installed-share-${UUID.randomUUID()}")
                runCatching {
                    require(temporaryRoot.mkdirs()) { "Laikinos APK vietos sukurti nepavyko" }
                    val staged = selected.mapIndexed { index, entry ->
                        val appDirectory = File(temporaryRoot, index.toString()).apply {
                            require(mkdirs()) { "Laikinos APK vietos sukurti nepavyko" }
                        }
                        graph.fileCategories.stageInstalledApp(entry, appDirectory)
                    }
                    graph.localShare.sharePrepared(staged).getOrThrow()
                }.also { temporaryRoot.deleteRecursively() }
            } else {
                graph.localShare.share(selected.map(FileEntry::absolutePath))
            }
            result.fold(
                onSuccess = {
                    clearFileCategorySelection()
                    message("Atidarytas bendrinimo pasirinkimas")
                },
                onFailure = { message(it.message ?: "Bendrinimo atidaryti nepavyko", true) },
            )
        }
    }

    fun installedAppExportCancelled() {
        message("APK išsaugojimas atšauktas")
    }

    fun shareSelection() {
        val paths = _panel.value.selectedPaths
        if (paths.isEmpty()) return
        viewModelScope.launch {
            graph.localShare.share(paths).fold(
                onSuccess = { count ->
                    clearSelection()
                    message(if (count == 1) "Atidarytas bendrinimo pasirinkimas" else "Bendrinimui paruošta: $count")
                },
                onFailure = { message(it.message ?: "Bendrinimo atidaryti nepavyko", true) },
            )
        }
    }

    fun shareEntry(path: String) {
        viewModelScope.launch {
            graph.localShare.share(listOf(path)).onFailure {
                message(it.message ?: "Bendrinimo atidaryti nepavyko", true)
            }
        }
    }

    fun shareEntries(paths: Collection<String>) {
        val selected = paths.distinct().take(1_001)
        if (selected.isEmpty()) return
        if (selected.size > 1_000) {
            message("Vienu metu galima bendrinti iki 1 000 elementų", true)
            return
        }
        viewModelScope.launch {
            graph.localShare.share(selected).onFailure {
                message(it.message ?: "Bendrinimo atidaryti nepavyko", true)
            }
        }
    }

    fun bookmarkSelection() {
        bookmarkPaths(_panel.value.selectedPaths)
        clearSelection()
    }

    fun bookmarkPath(path: String) = bookmarkPaths(listOf(path))

    /** Adds the paths to bookmarks, or removes them when every one is already bookmarked. */
    private fun bookmarkPaths(paths: Collection<String>) {
        runCatching {
            val current = _homeCustomization.value
            val normalized = paths.asSequence().distinct().map { File(it).absolutePath }.toList()
            val shortcutsByPath = current.shortcuts.groupBy(HomeShortcut::path)
            if (normalized.isNotEmpty() && normalized.all { it in shortcutsByPath }) {
                val updated = normalized.flatMap { shortcutsByPath.getValue(it) }.fold(current) { value, shortcut ->
                    HomeCustomizationRules.removeShortcut(value, shortcut.id)
                }
                graph.navigation.setHomeCustomization(updated, homeBuiltInShortcuts) to false
            } else {
                val candidates = normalized.map(::File).filter(File::exists)
                    .filterNot { it.absolutePath in shortcutsByPath }
                require(candidates.isNotEmpty()) { "Pasirinktos vietos jau pažymėtos" }
                val updated = candidates.fold(current) { value, file ->
                    HomeCustomizationRules.addShortcut(
                        value,
                        HomeShortcut(
                            id = "custom.${UUID.randomUUID()}",
                            title = file.name.ifBlank { file.absolutePath },
                            path = file.absolutePath,
                        ),
                    )
                }
                graph.navigation.setHomeCustomization(updated, homeBuiltInShortcuts) to true
            }
        }.onSuccess { (updated, added) ->
            _homeCustomization.value = updated
            message(if (added) "Pridėta prie žymelių" else "Pašalinta iš žymelių")
        }.onFailure { message(it.message ?: "Žymelės pridėti nepavyko", true) }
    }

    fun openQuickPath(path: String) {
        val file = File(path)
        if (file.isDirectory) navigate(path)
        else if (file.isFile) open(graph.localFiles.toEntry(file))
        else message("Vieta nebeegzistuoja", true)
    }

    fun openQuickPathFromHome(path: String) {
        val file = File(path)
        when {
            file.isDirectory -> openLocalDirectoryFromHome(path)
            file.isFile -> {
                open(graph.localFiles.toEntry(file))
            }
            else -> message("Vieta nebeegzistuoja", true)
        }
    }

    fun openHomeShortcut(shortcutId: String, path: String) {
        openQuickPathFromHome(path)
    }

    fun openStorageRoot(root: StorageRoot) {
        val target = runCatching { File(root.path).canonicalPath }.getOrElse {
            message(it.message ?: "Saugykla nepasiekiama", true)
            return
        }
        val knownRoot = _roots.value.firstOrNull { candidate ->
            runCatching { File(candidate.path).canonicalPath == target }.getOrDefault(false)
        }
        if (knownRoot == null || !File(target).isDirectory) {
            message("Saugykla nebeprijungta", true)
            return
        }

        openLocalDirectoryFromHome(target)
    }

    private fun openLocalDirectoryFromHome(targetPath: String) {
        val target = runCatching { File(targetPath).canonicalPath }.getOrElse {
            message(it.message ?: "Kelias nepasiekiamas", true)
            return
        }
        if (!File(target).isDirectory) {
            message("Vieta nebeegzistuoja", true)
            return
        }

        _section.value = AppSection.FILES
        _filesHomeVisible.value = false
        val displaySettings = savedDirectoryDisplaySettings(target)
        val sortSettings = savedDirectorySortSettings(target)
        fileScrollPositions.reset(_tabs.value.activeTabId, target)
        _panel.update { state ->
            state.copy(
                path = target,
                entries = emptyList(),
                selectedPaths = emptySet(),
                loading = true,
                listingScannedEntries = 0,
                listingMetadataEntries = 0,
                listingTruncated = false,
                error = null,
                backHistory = emptyList(),
                forwardHistory = emptyList(),
                returnToHomeAtBoundary = true,
            ).withDirectoryDisplaySettings(displaySettings)
                .withDirectorySortSettings(sortSettings)
        }
        syncActiveTab()
        refreshPanel()
    }

    fun clearRecents() {
        runCatching { graph.navigation.clearRecents(); _recents.value = emptyList() }
            .onFailure { message(it.message ?: "Istorijos išvalyti nepavyko", true) }
    }

    fun saveSearch(name: String, roots: List<String>, filters: SearchFilters) {
        runCatching { _savedSearches.value = graph.navigation.saveSearch(name, roots, filters) }
            .onFailure { message(it.message ?: "Paieškos išsaugoti nepavyko", true) }
    }

    fun runSavedSearch(search: SavedSearch) {
        _section.value = AppSection.ANALYZE
        search(search.filters(), search.rootPaths)
    }

    fun removeSavedSearch(id: String) {
        runCatching { _savedSearches.value = graph.navigation.removeSearch(id) }
            .onFailure { message(it.message ?: "Paieškos pašalinti nepavyko", true) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        runCatching { graph.appLock.setEnabled(enabled) }
            .onSuccess { message(if (enabled) "Programos užraktas įjungtas" else "Programos užraktas išjungtas") }
            .onFailure { message(it.message ?: "Užrakto nustatymo pakeisti nepavyko", true) }
    }

    fun restoreTrash(id: String) {
        viewModelScope.launch {
            graph.trash.restore(id).fold(
                onSuccess = { refreshTrash(); refreshPanel() },
                onFailure = { message(it.message ?: "Atkurti nepavyko", true) },
            )
        }
    }

    fun deleteTrashForever(id: String) {
        graph.operationManager.submit("Šalinama iš šiukšliadėžės") {
            graph.trash.deleteForever(id, this).fold(
                onSuccess = { refreshTrash() },
                onFailure = { message(it.message ?: "Ištrinti nepavyko", true) },
            )
        }.onFailure { message(it.message ?: "Trynimo pradėti nepavyko", true) }
    }

    private fun openClonedAppStorage() {
        val normalLocation = ClonedAppStorage.normalLocation(File(initialPrimaryPath))
        if (normalLocation != null) {
            openLocalDirectoryFromHome(normalLocation.absolutePath)
            return
        }
        if (graph.advancedAccess.state.value.activeBackend in setOf(
                AdvancedAccessBackend.ROOT,
                AdvancedAccessBackend.SHIZUKU_ROOT,
            )
        ) {
            openAdvancedBrowser(ClonedAppStorage.PRIVILEGED_PROFILE_ROOT)
            return
        }
        if (graph.advancedAccess.state.value.activeBackend == AdvancedAccessBackend.SHIZUKU_SHELL) {
            message(
                "Sistemos šaknis / atveriama tik su Root arba Shizuku root prieiga. Tai nėra įprastas Android leidimas ir Shovel jo neapeina.",
                true,
            )
            return
        }
        message(
            "Klonuotų programų saugykla priklauso kitam Android profiliui. Jai reikia Root arba Shizuku su root teisėmis.",
            true,
        )
    }

    fun refreshSafLocations() {
        viewModelScope.launch { _safLocations.value = graph.safFiles.locations() }
    }

    fun addSafLocation(uri: android.net.Uri, title: String) {
        viewModelScope.launch {
            graph.safFiles.addLocation(uri, title).fold(
                onSuccess = { refreshSafLocations() },
                onFailure = { message(it.message ?: "Vietos pridėti nepavyko", true) },
            )
        }
    }

    fun removeSafLocation(uri: String) {
        viewModelScope.launch {
            graph.safFiles.removeLocation(uri).fold(
                onSuccess = {
                    if (_safBrowser.value.location?.uri == uri) closeSafBrowser()
                    refreshSafLocations()
                },
                onFailure = { message(it.message ?: "Vietos pašalinti nepavyko", true) },
            )
        }
    }

    fun revealLocalEntry(path: String) {
        val file = runCatching { File(path).canonicalFile }.getOrElse {
            message("Vieta nebeegzistuoja", true)
            return
        }
        val parent = file.parentFile?.takeIf(File::isDirectory) ?: run {
            message("Vieta nebeegzistuoja", true)
            return
        }
        openLocalDirectoryFromHome(parent.absolutePath)
        viewModelScope.launch {
            panelRefreshJob?.join()
            _panel.update { state ->
                if (state.path == parent.absolutePath && state.entries.any { it.absolutePath == file.absolutePath }) {
                    state.copy(selectedPaths = setOf(file.absolutePath))
                } else state
            }
        }
    }

    fun renameSafLocation(uri: String, title: String) {
        viewModelScope.launch {
            graph.safFiles.renameLocation(uri, title).fold(
                onSuccess = { refreshSafLocations() },
                onFailure = { message(it.message ?: "Pervadinti nepavyko", true) },
            )
        }
    }

    fun openSafLocation(location: SafLocation) {
        val display = savedDirectoryDisplaySettings(safDirectoryIdentity(location.uri))
        val sort = runCatching { graph.navigation.directorySortSettings(safDirectoryIdentity(location.uri)) }
            .onFailure { message(it.message ?: "Dokumentų vietos rūšiavimo nustatymo perskaityti nepavyko", true) }
            .getOrDefault(DirectorySortSettings())
        _safBrowser.value = SafBrowserUiState(
            location = location,
            currentUri = location.uri,
            title = location.title,
            sortMode = sort.mode,
            sortDirection = sort.direction,
        ).withSafDisplaySettings(display)
        refreshSafBrowser()
    }

    fun openSafEntry(entry: SafEntry) {
        if (entry.directory) {
            val state = _safBrowser.value
            val current = state.currentUri ?: return
            val display = savedDirectoryDisplaySettings(safDirectoryIdentity(entry.uri))
            val sort = runCatching { graph.navigation.directorySortSettings(safDirectoryIdentity(entry.uri)) }
                .getOrDefault(DirectorySortSettings())
            _safBrowser.update {
                it.copy(
                    currentUri = entry.uri,
                    title = entry.name,
                    backStack = (it.backStack + (current to it.title)).takeLast(64),
                    entries = emptyList(),
                    sortMode = sort.mode,
                    sortDirection = sort.direction,
                ).withSafDisplaySettings(display)
            }
            refreshSafBrowser()
        } else {
            val application = getApplication<Application>()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(entry.uri), entry.mimeType ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                application.startActivity(
                    Intent.createChooser(intent, application.appString(R.string.open_with_chooser_short))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
                .onFailure { message(it.message ?: "Failo atidaryti nepavyko", true) }
        }
    }

    fun navigateSafBack(): Boolean {
        val state = _safBrowser.value
        val previous = state.backStack.lastOrNull() ?: return false
        val display = savedDirectoryDisplaySettings(safDirectoryIdentity(previous.first))
        val sort = runCatching { graph.navigation.directorySortSettings(safDirectoryIdentity(previous.first)) }
            .getOrDefault(DirectorySortSettings())
        _safBrowser.update {
            it.copy(
                currentUri = previous.first,
                title = previous.second,
                backStack = it.backStack.dropLast(1),
                entries = emptyList(),
                sortMode = sort.mode,
                sortDirection = sort.direction,
            ).withSafDisplaySettings(display)
        }
        refreshSafBrowser()
        return true
    }

    fun refreshSafBrowser() {
        val uri = _safBrowser.value.currentUri ?: return
        safBrowserJob?.cancel()
        safBrowserJob = viewModelScope.launch {
            _safBrowser.update { current ->
                if (current.currentUri == uri) current.copy(loading = true, error = null) else current
            }
            graph.safFiles.list(uri).fold(
                onSuccess = { entries ->
                    _safBrowser.update { current ->
                        if (current.currentUri == uri) current.copy(entries = entries, loading = false) else current
                    }
                },
                onFailure = { error ->
                    _safBrowser.update { current ->
                        if (current.currentUri == uri) {
                            current.copy(entries = emptyList(), loading = false, error = error.message)
                        } else {
                            current
                        }
                    }
                },
            )
        }
    }

    fun toggleSafLayout() {
        val state = _safBrowser.value
        setSafDisplaySettings(
            state.safDisplaySettings().copy(
                layoutMode = if (state.grid) DirectoryLayoutMode.LIST else DirectoryLayoutMode.GRID,
            ),
        )
    }

    fun toggleSafThumbnails() {
        val state = _safBrowser.value
        setSafDisplaySettings(state.safDisplaySettings().copy(showThumbnails = !state.showThumbnails))
    }

    fun setSafDisplaySettings(settings: DirectoryDisplaySettings) {
        val uri = _safBrowser.value.currentUri ?: return
        runCatching { graph.navigation.setDirectoryDisplaySettings(safDirectoryIdentity(uri), settings) }
            .onSuccess {
                _safBrowser.update { current ->
                    if (current.currentUri == uri) current.withSafDisplaySettings(settings) else current
                }
            }
            .onFailure { message(it.message ?: "Dokumentų vietos rodinio nustatymo išsaugoti nepavyko", true) }
    }

    fun setSafSort(mode: SortMode, direction: SortDirection) {
        val uri = _safBrowser.value.currentUri ?: return
        runCatching {
            graph.navigation.setDirectorySortSettings(safDirectoryIdentity(uri), DirectorySortSettings(mode, direction))
        }.onSuccess {
            _safBrowser.update { current ->
                if (current.currentUri == uri) current.copy(sortMode = mode, sortDirection = direction) else current
            }
        }.onFailure { message(it.message ?: "Dokumentų vietos rūšiavimo nustatymo išsaugoti nepavyko", true) }
    }

    fun createSafDirectory(name: String) = mutateSaf { uri -> graph.safFiles.createDirectory(uri, name).map { Unit } }

    fun createSafFile(name: String) = mutateSaf { uri -> graph.safFiles.createFile(uri, name).map { Unit } }

    fun renameSafEntry(entry: SafEntry, name: String) {
        viewModelScope.launch {
            graph.safFiles.rename(entry.uri, name).fold(
                onSuccess = { refreshSafBrowser() },
                onFailure = { message(it.message ?: "Pervadinti nepavyko", true) },
            )
        }
    }

    fun deleteSafEntry(entry: SafEntry) {
        graph.operationManager.submit("Šalinamas ${entry.name}") {
            setTotals(1, entry.sizeBytes.takeIf { !entry.directory })
            checkpoint()
            graph.safFiles.delete(entry.uri).getOrThrow()
            progress(itemDelta = 1, byteDelta = entry.sizeBytes.takeIf { !entry.directory } ?: 0L, currentName = entry.name)
            refreshSafBrowser()
        }.onFailure { message(it.message ?: "Trynimo pradėti nepavyko", true) }
    }

    suspend fun loadSafSelectionInfo(uris: Collection<String>): Result<FileSelectionSummary> =
        graph.safFiles.selectionInfo(uris)

    fun copyLocalToSaf(localPath: String) {
        val destinationUri = _safBrowser.value.currentUri ?: return
        val source = File(localPath)
        graph.operationManager.submit("Kopijuojama į Android vietą") {
            graph.safFiles.copyFromLocal(source, destinationUri, this).getOrThrow()
        }.onFailure { message(it.message ?: "Kopijavimo pradėti nepavyko", true) }
    }

    fun copySafToLocal(entry: SafEntry) {
        val destination = File(_panel.value.path)
        graph.operationManager.submit("Kopijuojama iš Android vietos") {
            graph.safFiles.copyToLocal(entry.uri, destination, this).getOrThrow()
        }.onFailure { message(it.message ?: "Kopijavimo pradėti nepavyko", true) }
    }

    fun closeSafBrowser() {
        safBrowserJob?.cancel()
        safBrowserJob = null
        _safBrowser.value = SafBrowserUiState()
    }

    suspend fun listLocalDirectoryForUpload(path: String): Result<List<FileEntry>> = graph.localFiles.list(
        directoryPath = path,
        includeHidden = false,
        sortMode = SortMode.NAME,
        sortDirection = SortDirection.ASCENDING,
    )

    fun cancelOperation(id: String) = graph.durableTransfers.cancel(id)
    fun pauseOperation(id: String) = graph.operationManager.pause(id)
    fun resumeOperation(id: String) = graph.operationManager.resume(id)
    fun retryOperation(id: String) {
        graph.durableTransfers.retry(id).onFailure { error ->
            message(error.message ?: "Operacijos pakartoti nepavyko", true)
        }
    }
    fun dismissFinishedOperations() = graph.operationManager.dismissFinished()

    fun panelState(): PanelUiState = _panel.value

    override fun onCleared() {
        clearArchiveSession()
        workspaceSaveRequests.trySend(currentWorkspace())
        workspaceSaveRequests.close()
        // Let queued writes finish, then release the workers' reference to this screen.
        searchDraftSaveRequests.close()
        recentFilesJob?.cancel()
        recentFilesJob = null
        fileEditRequestId += 1
        fileEditJob?.cancel()
        val editSession = _fileEditState.value.session
        val previewTarget = _preview.value
        _fileEditState.value = FileEditUiState()
        _preview.value = null
        graph.applicationScope.launch {
            graph.editSessions.discard(editSession)
            graph.privilegedFiles.discardPreview(previewTarget.privilegedCachedFile())
            discardArchiveMaterializations()
        }
        super.onCleared()
    }

    private fun savedThumbnailMode(directoryIdentity: String): Boolean = runCatching {
        graph.navigation.thumbnailsEnabled(directoryIdentity)
    }.onFailure {
        message(it.message ?: "Katalogo vaizdo nustatymo perskaityti nepavyko", true)
    }.getOrDefault(false)

    private fun savedDirectoryDisplaySettings(
        directoryIdentity: String,
        fallbackGrid: Boolean = false,
    ): DirectoryDisplaySettings {
        val saved = runCatching { graph.navigation.directoryDisplaySettings(directoryIdentity) }
            .onFailure { message(it.message ?: "Katalogo vaizdo nustatymo perskaityti nepavyko", true) }
            .getOrNull()
        return saved ?: DirectoryDisplaySettings(
            layoutMode = if (fallbackGrid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
            showThumbnails = savedThumbnailMode(directoryIdentity),
        )
    }

    private fun savedDirectorySortSettings(directoryIdentity: String): DirectorySortSettings = runCatching {
        graph.navigation.directorySortSettings(directoryIdentity)
    }.onFailure {
        message(it.message ?: "Katalogo rūšiavimo nustatymo perskaityti nepavyko", true)
    }.getOrDefault(DirectorySortSettings())

    private fun fileCategoryIdentity(category: FileCategory): String =
        "virtual:category/${category.name.lowercase()}"

    private fun safDirectoryIdentity(uri: String): String = "saf:$uri"

    private fun trashDirectoryIdentity(itemId: String?, relativePath: String): String = if (itemId == null) {
        "virtual:trash/root"
    } else {
        "virtual:trash/$itemId/${TrashPathRules.normalize(relativePath)}"
    }

    private fun WorkspaceTab.toPanelUiState(): PanelUiState {
        val saved = runCatching { graph.navigation.directoryDisplaySettings(path) }.getOrNull()
        val savedSort = runCatching { graph.navigation.directorySortSettingsOverride(path) }.getOrNull()
        val settings = saved ?: DirectoryDisplaySettings(
            layoutMode = if (grid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
            showThumbnails = runCatching { graph.navigation.thumbnailsEnabled(path) }.getOrDefault(false),
        )
        return PanelUiState(
            path = path,
            sortMode = savedSort?.mode ?: sortMode,
            sortDirection = savedSort?.direction ?: sortDirection,
            includeHidden = includeHidden,
            backHistory = backHistory,
            forwardHistory = forwardHistory,
            returnToHomeAtBoundary = returnToHomeAtBoundary,
        ).withDirectoryDisplaySettings(settings)
    }

    private fun PanelUiState.directoryDisplaySettings(): DirectoryDisplaySettings = DirectoryDisplaySettings(
        layoutMode = if (grid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
        iconScalePercent = iconScalePercent,
        spacingScalePercent = spacingScalePercent,
        gridColumns = gridColumns,
        gridStyle = gridStyle,
        showThumbnails = showThumbnails,
    )

    private fun FileCategoryUiState.fileCategoryDisplaySettings(): DirectoryDisplaySettings = DirectoryDisplaySettings(
        layoutMode = if (grid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
        iconScalePercent = iconScalePercent,
        spacingScalePercent = spacingScalePercent,
        gridColumns = gridColumns,
        gridStyle = gridStyle,
        showThumbnails = showThumbnails,
    )

    private fun FileCategoryUiState.withFileCategoryDisplaySettings(
        settings: DirectoryDisplaySettings,
    ): FileCategoryUiState = copy(
        grid = settings.layoutMode == DirectoryLayoutMode.GRID,
        iconScalePercent = settings.iconScalePercent,
        spacingScalePercent = settings.spacingScalePercent,
        gridColumns = settings.gridColumns,
        gridStyle = settings.gridStyle,
        showThumbnails = settings.showThumbnails,
    )

    private fun TrashBrowserUiState.trashDisplaySettings(): DirectoryDisplaySettings = DirectoryDisplaySettings(
        layoutMode = if (grid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
        iconScalePercent = iconScalePercent,
        spacingScalePercent = spacingScalePercent,
        gridColumns = gridColumns,
        gridStyle = gridStyle,
        showThumbnails = showThumbnails,
    )

    private fun TrashBrowserUiState.withTrashDisplaySettings(settings: DirectoryDisplaySettings): TrashBrowserUiState = copy(
        grid = settings.layoutMode == DirectoryLayoutMode.GRID,
        iconScalePercent = settings.iconScalePercent,
        spacingScalePercent = settings.spacingScalePercent,
        gridColumns = settings.gridColumns,
        gridStyle = settings.gridStyle,
        showThumbnails = settings.showThumbnails,
    )

    private fun TrashBrowserUiState.withTrashSortSettings(settings: DirectorySortSettings): TrashBrowserUiState = copy(
        sortMode = settings.mode,
        sortDirection = settings.direction,
    )

    private fun SafBrowserUiState.safDisplaySettings(): DirectoryDisplaySettings = DirectoryDisplaySettings(
        layoutMode = if (grid) DirectoryLayoutMode.GRID else DirectoryLayoutMode.LIST,
        iconScalePercent = iconScalePercent,
        spacingScalePercent = spacingScalePercent,
        gridColumns = gridColumns,
        gridStyle = gridStyle,
        showThumbnails = showThumbnails,
    )

    private fun SafBrowserUiState.withSafDisplaySettings(settings: DirectoryDisplaySettings): SafBrowserUiState = copy(
        grid = settings.layoutMode == DirectoryLayoutMode.GRID,
        iconScalePercent = settings.iconScalePercent,
        spacingScalePercent = settings.spacingScalePercent,
        gridColumns = settings.gridColumns,
        gridStyle = settings.gridStyle,
        showThumbnails = settings.showThumbnails,
    )

    private fun PanelUiState.withDirectoryDisplaySettings(settings: DirectoryDisplaySettings): PanelUiState = copy(
        grid = settings.layoutMode == DirectoryLayoutMode.GRID,
        iconScalePercent = settings.iconScalePercent,
        spacingScalePercent = settings.spacingScalePercent,
        gridColumns = settings.gridColumns,
        gridStyle = settings.gridStyle,
        showThumbnails = settings.showThumbnails,
    )

    private fun PanelUiState.withDirectorySortSettings(settings: DirectorySortSettings): PanelUiState = copy(
        sortMode = settings.mode,
        sortDirection = settings.direction,
    )


    private fun PanelWorkspace.withGlobalDisplayDefaults(
        settings: DirectoryDisplaySettings,
        sortMode: SortMode,
        sortDirection: SortDirection,
    ): PanelWorkspace = copy(
        tabs = tabs.map { tab ->
            tab.copy(
                grid = settings.layoutMode == DirectoryLayoutMode.GRID,
                sortMode = sortMode,
                sortDirection = sortDirection,
            )
        },
    )

    private fun syncActiveTab(persist: Boolean = true) {
        val state = _panel.value
        _tabs.update { workspace ->
            workspace.copy(
                tabs = workspace.tabs.map { tab ->
                    if (tab.id == workspace.activeTabId) {
                        tab.copy(
                            path = state.path,
                            backHistory = state.backHistory.takeLast(50),
                            forwardHistory = state.forwardHistory.takeLast(50),
                            sortMode = state.sortMode,
                            sortDirection = state.sortDirection,
                            includeHidden = state.includeHidden,
                            grid = state.grid,
                            returnToHomeAtBoundary = state.returnToHomeAtBoundary,
                        )
                    } else tab
                },
            )
        }
        if (persist) persistWorkspace()
    }

    private fun currentWorkspace(): WorkspaceSession = WorkspaceSession(workspace = _tabs.value)

    private fun persistWorkspace() {
        if (workspaceSaveRequests.trySend(currentWorkspace()).isFailure) {
            message("Darbo sesijos išsaugojimo eilė nepasiekiama", true)
        }
    }




    private fun PanelUiState.matchesListingRequest(request: PanelUiState): Boolean =
        path == request.path && includeHidden == request.includeHidden && sortMode == request.sortMode && sortDirection == request.sortDirection

    private fun scheduleBatchRenamePreview(immediate: Boolean) {
        batchRenamePreviewJob?.cancel()
        val snapshot = _batchRename.value
        if (!snapshot.open) return
        batchRenamePreviewJob = viewModelScope.launch {
            if (!immediate) delay(150)
            try {
                val preview = graph.batchRename.preview(snapshot.sourcePaths, snapshot.spec)
                if (_batchRename.value.open && _batchRename.value.sourcePaths == snapshot.sourcePaths && _batchRename.value.spec == snapshot.spec) {
                    _batchRename.update { it.copy(preview = preview, running = false, error = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (_batchRename.value.open && _batchRename.value.sourcePaths == snapshot.sourcePaths && _batchRename.value.spec == snapshot.spec) {
                    _batchRename.update { it.copy(preview = null, running = false, error = error.message) }
                }
            }
        }
    }

    private fun orderedSearchSelection(): List<String> {
        val state = _searchState.value
        return state.results.asSequence()
            .map(FileEntry::absolutePath)
            .filter { it in state.selectedPaths }
            .toList()
    }

    private fun rerunSearchIfVisible() {
        val state = _searchState.value
        if (state.roots.isNotEmpty()) search(state.filters, state.roots)
    }

    private fun message(text: String, error: Boolean = false, action: UiMessageAction? = null) {
        _messages.tryEmit(UiMessage(text, error, action))
    }

    private fun mutateSaf(operation: suspend (String) -> Result<Unit>) {
        val uri = _safBrowser.value.currentUri ?: return
        viewModelScope.launch {
            operation(uri).fold(
                onSuccess = { refreshSafBrowser() },
                onFailure = { message(it.message ?: "Veiksmas nepavyko", true) },
            )
        }
    }

    private fun closePreviewImmediately() {
        fileEditRequestId += 1
        fileEditJob?.cancel()
        fileEditJob = null
        val session = _fileEditState.value.session
        val target = _preview.value
        _fileEditState.value = FileEditUiState()
        val returnTarget = archiveReturnTarget.takeIf { target is PreviewTarget.ArchiveEntry }
        if (returnTarget != null) {
            archiveReturnTarget = null
            _preview.value = returnTarget
            graph.applicationScope.launch {
                graph.editSessions.discard(session)
                target?.let { (it as? PreviewTarget.ArchiveEntry)?.file?.file?.delete() }
            }
            return
        }
        archiveReturnTarget = null
        _preview.value = null
        clearArchiveSession()
        graph.applicationScope.launch {
            val editRemoved = graph.editSessions.discard(session)
            val privilegedPreviewRemoved = graph.privilegedFiles.discardPreview(target.privilegedCachedFile())
            val archivePreviewRemoved = discardArchiveMaterializations()
            if (!editRemoved || !privilegedPreviewRemoved || !archivePreviewRemoved) {
                message("Laikinos redagavimo kopijos pašalinti nepavyko", true)
            }
        }
    }

    private fun PreviewTarget?.privilegedCachedFile(): File? = when (this) {
        is PreviewTarget.PrivilegedFile -> cachedFile
        else -> null
    }

    private fun PreviewTarget?.archiveFile(): File? = when (this) {
        is PreviewTarget.Archive -> file.file
        else -> null
    }

    private fun PreviewTarget?.archiveEntries(): List<ArchiveEntryInfo> = when (this) {
        is PreviewTarget.Archive -> entries
        else -> emptyList()
    }

    private fun updateArchivePreviewEntries(expected: PreviewTarget, entries: List<ArchiveEntryInfo>) {
        _preview.update { current ->
            when {
                current !== expected -> current
                current is PreviewTarget.Archive -> current.copy(entries = entries)
                else -> current
            }
        }
    }

    private suspend fun materializeArchiveEntry(entryPath: String, maximumBytes: Long): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            archiveMaterializationMutex.withLock {
                val target = _preview.value
                val archive = target.archiveFile() ?: throw IllegalStateException("Archyvas nebeatidarytas")
                val normalizedPath = ArchiveMutationRules.normalizePath(entryPath)
                val info = target.archiveEntries().firstOrNull {
                    !it.directory && runCatching { ArchiveMutationRules.normalizePath(it.name) }.getOrNull() == normalizedPath
                } ?: throw IllegalArgumentException("Archyvo įrašas neberastas")
                require(info.sizeBytes in 0..maximumBytes) { "Archyvo įrašas per didelis greitajai peržiūrai" }
                prepareArchiveMaterializationCache(archive)
                archiveMaterializedFiles[normalizedPath]?.takeIf(File::isFile)?.let { return@withLock it }
                require(archiveMaterializedFiles.size < MAX_ARCHIVE_MATERIALIZED_ITEMS) { "Archyvo peržiūrų riba pasiekta" }
                val extension = info.name.substringAfterLast('.', "").take(10).filter(Char::isLetterOrDigit)
                val destination = File(
                    requireNotNull(archiveMaterializationRoot),
                    "${UUID.randomUUID()}${extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()}",
                )
                graph.archives.extractEntry(archive, info.name, destination, null, archivePasswordFor(archive))
                archiveMaterializedFiles[normalizedPath] = destination
                destination
            }
        }
    }

    private fun prepareArchiveMaterializationCache(archive: File) {
        val key = "${archive.absolutePath}:${archive.length()}:${archive.lastModified()}"
        if (archiveMaterializationKey == key && archiveMaterializationRoot?.isDirectory == true) return
        archiveMaterializationRoot?.deleteRecursively()
        archiveMaterializedFiles.clear()
        val root = File(getApplication<Application>().cacheDir, "archive-previews/${UUID.randomUUID()}")
        require(root.mkdirs()) { "Archyvo peržiūros talpyklos sukurti nepavyko" }
        archiveMaterializationKey = key
        archiveMaterializationRoot = root
    }

    private suspend fun discardArchiveMaterializations(): Boolean = archiveMaterializationMutex.withLock {
        archiveMaterializedFiles.clear()
        archiveMaterializationKey = null
        val root = archiveMaterializationRoot
        archiveMaterializationRoot = null
        root == null || !root.exists() || root.deleteRecursively()
    }

    private fun saveContentOrigin(session: EditSession, forceOverwrite: Boolean): EditSaveResult {
        val origin = session.origin as? EditOrigin.Content ?: error("Edit session is not a content URI")
        val uri = Uri.parse(origin.uri)
        val current = currentContentRevision(uri)
        if (!forceOverwrite && !session.originRevision.hasSameContent(current)) {
            return EditSaveResult.Conflict(EditConflict(origin.label, session.originRevision, current))
        }
        require(origin.canWrite) { "Originalas skirtas tik skaityti; naudokite „Išsaugoti kaip“" }
        val verified = writeContentDestination(session, uri)
        require(session.workingRevision.hasSameContent(verified)) { "Išsaugoto failo patikra nepavyko" }
        return EditSaveResult.Saved(verified)
    }

    private fun currentContentRevision(uri: Uri): FileRevision? {
        val resolver = getApplication<Application>().contentResolver
        return try {
            graph.editSessions.revisionFromStream(modifiedAtMillis = null) {
                resolver.openInputStream(uri) ?: throw FileNotFoundException("Original content URI is unavailable")
            }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private fun contentDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun writeContentDestination(session: EditSession, destination: Uri): FileRevision {
        require(destination.scheme == "content") { "Pasirinkta vieta neįrašoma per Android dokumentų sistemą" }
        val resolver = getApplication<Application>().contentResolver
        graph.editSessions.writeWorkingCopy(session) {
            openContentOutput(resolver, destination)
        }
        return graph.editSessions.revisionFromStream(modifiedAtMillis = null) {
            resolver.openInputStream(destination) ?: throw FileNotFoundException("Išsaugoto failo patikrinti nepavyko")
        }
    }

    private fun openContentOutput(resolver: ContentResolver, uri: Uri): java.io.OutputStream {
        val readWriteTruncateOutput = runCatching { resolver.openOutputStream(uri, "rwt") }.getOrNull()
        return readWriteTruncateOutput
            ?: runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: throw FileNotFoundException("Pasirinkta vieta nesuteikė įrašomo srauto")
    }

}
