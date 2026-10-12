package com.shovel.filemanager

import android.app.Application
import com.shovel.filemanager.advanced.AdvancedAccessManager
import com.shovel.filemanager.advanced.PrivilegedFileRepository
import com.shovel.filemanager.archive.ArchiveEngine
import com.shovel.filemanager.data.LocalFileRepository
import com.shovel.filemanager.data.ContentFileRepository
import com.shovel.filemanager.data.NavigationRepository
import com.shovel.filemanager.data.RecentFileRepository
import com.shovel.filemanager.data.SafFileRepository
import com.shovel.filemanager.data.TrashRepository
import com.shovel.filemanager.data.TrashRetentionSettings
import com.shovel.filemanager.data.TrashRetentionScheduler
import com.shovel.filemanager.data.UiPreferenceRepository
import com.shovel.filemanager.data.WorkspaceSessionRepository
import com.shovel.filemanager.cleanup.DeviceCleanupRepository
import com.shovel.filemanager.cleanup.PrivilegedAppProcessRepository
import com.shovel.filemanager.data.FileCategoryRepository
import com.shovel.filemanager.data.FileSelectionInfoScanner
import com.shovel.filemanager.editing.EditSessionStore
import com.shovel.filemanager.editing.RemoteEditSaver
import com.shovel.filemanager.editing.ThreeWayTextMerge
import com.shovel.filemanager.network.NetworkProfileStore
import com.shovel.filemanager.network.RemoteClientFactory
import com.shovel.filemanager.network.RemoteCopyEngine
import com.shovel.filemanager.operations.FileOperationManager
import com.shovel.filemanager.operations.FileOperationForegroundService
import com.shovel.filemanager.operations.LocalFileOperator
import com.shovel.filemanager.operations.BatchRenameEngine
import com.shovel.filemanager.operations.DurableTransferCoordinator
import com.shovel.filemanager.operations.DurableTransferRepository
import com.shovel.filemanager.pdfsigning.PdfVisualSignatureEngine
import com.shovel.filemanager.pdfsigning.SignatureLibraryRepository
import com.shovel.filemanager.search.FileSearchEngine
import com.shovel.filemanager.search.SimilarImageEngine
import com.shovel.filemanager.security.CredentialVault
import com.shovel.filemanager.security.AppLockRepository
import com.shovel.filemanager.sharing.LocalShareManager
import com.shovel.filemanager.ui.theme.AppearanceRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AFFileManagerApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        graph = AppGraph(this)
        graph.applicationScope.launch {
            graph.durableTransfers.restore()
            graph.trashRetentionScheduler.synchronize(graph.trashRetentionSettings.load())
        }
    }
}

class AppGraph(application: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val advancedAccess = AdvancedAccessManager(application)
    val privilegedFiles = PrivilegedFileRepository(application, advancedAccess)
    val sharedStorageIndex: com.shovel.filemanager.data.SharedStorageIndex = com.shovel.filemanager.data.SharedStorageIndex(application).apply {
        onChanged = { com.shovel.filemanager.data.FileCategory.entries.forEach(fileCategories::invalidate) }
    }
    val localFiles = LocalFileRepository(application, sharedStorageIndex::changed)
    val recentFiles = RecentFileRepository(application, localFiles)
    val fileCategories = FileCategoryRepository(application, localFiles)
    val deviceCleanup by lazy { DeviceCleanupRepository(application) }
    val privilegedApps by lazy { PrivilegedAppProcessRepository(application, advancedAccess) }
    val fileSelectionInfo by lazy { FileSelectionInfoScanner() }
    val contentFiles by lazy { ContentFileRepository(application) }
    val navigation = NavigationRepository(application)
    val uiPreferences = UiPreferenceRepository(application)
    val appearance = AppearanceRepository(application)
    val workspaceSession = WorkspaceSessionRepository(application)
    val editSessions by lazy { EditSessionStore(application.cacheDir) }
    val pdfSignatures by lazy { PdfVisualSignatureEngine(application.cacheDir) }
    val signatureLibrary by lazy {
        SignatureLibraryRepository(java.io.File(application.filesDir, "saved-signatures"))
    }
    val remoteEdits by lazy { RemoteEditSaver(editSessions) }
    val textMerge by lazy { ThreeWayTextMerge() }
    val localFileOperator by lazy { LocalFileOperator(onMutation = sharedStorageIndex::changed) }
    val batchRename by lazy { BatchRenameEngine(onMutation = sharedStorageIndex::changed) }
    val operationManager = FileOperationManager(applicationScope) {
        FileOperationForegroundService.start(application)
    }
    val durableTransferRepository = DurableTransferRepository(application)
    val durableTransfers = DurableTransferCoordinator(operationManager, durableTransferRepository, onMutation = sharedStorageIndex::changed)
    val trash = TrashRepository(application, onMutation = sharedStorageIndex::changed)
    val trashRetentionSettings = TrashRetentionSettings(application)
    val trashRetentionScheduler = TrashRetentionScheduler(application)
    val search = FileSearchEngine(localFiles)
    val similarImages by lazy { SimilarImageEngine() }
    val archives = ArchiveEngine(onMutation = sharedStorageIndex::changed)
    val localShare by lazy { LocalShareManager(application, archives) }
    val credentialVault = CredentialVault()
    val appLock = AppLockRepository(application)
    val networkProfiles = NetworkProfileStore(application, credentialVault)
    val remoteClients = RemoteClientFactory()
    val remoteCopies by lazy { RemoteCopyEngine(sharedStorageIndex::changed) }
    val safFiles = SafFileRepository(application, sharedStorageIndex::changed)
}
