package com.shovel.filemanager.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.shovel.filemanager.ui.theme.ShovelAlertDialog as AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.shovel.filemanager.data.OptionalFeature
import com.shovel.filemanager.ui.components.LocalFeatureVisibility
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.shovel.filemanager.ui.theme.ShovelDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.shovel.filemanager.operations.OperationStatus
import com.shovel.filemanager.ui.components.OperationProgressDialog
import com.shovel.filemanager.IncomingViewRequest
import com.shovel.filemanager.ui.preview.FilePreviewDialog
import com.shovel.filemanager.ui.components.BatchRenameDialog
import com.shovel.filemanager.ui.components.BackgroundPlaybackBar
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.UiTranslator
import com.shovel.filemanager.ui.preview.ArchivePasswordDialog
import com.shovel.filemanager.ui.screens.OperationsScreen
import com.shovel.filemanager.ui.screens.AnalyzeScreen
import com.shovel.filemanager.ui.screens.AdvancedStorageBrowserDialog
import com.shovel.filemanager.ui.screens.FilesScreen
import com.shovel.filemanager.ui.screens.ToolsScreen
import com.shovel.filemanager.ui.screens.TrashBrowserDialog
import com.shovel.filemanager.ui.screens.SafBrowserDialog
import java.io.File

private data class SectionDestination(
    val section: AppSection,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    SectionDestination(AppSection.FILES, "Failai", Icons.Rounded.Folder),
    SectionDestination(AppSection.ANALYZE, "Analizė", Icons.Rounded.Analytics),
    SectionDestination(AppSection.OPERATIONS, "Operations", Icons.Rounded.Sync),
    SectionDestination(AppSection.TOOLS, "Daugiau", Icons.Rounded.MoreHoriz),
)

@Composable
fun ShovelApp(
    viewModel: MainViewModel = viewModel(),
    incomingViewRequest: IncomingViewRequest? = null,
    onIncomingViewRequestConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val interfaceLanguage = LocalConfiguration.current.locales[0].language
    val lifecycleOwner = LocalLifecycleOwner.current
    val section by viewModel.section.collectAsStateWithLifecycle()
    val featureVisibility by viewModel.featureVisibility.collectAsStateWithLifecycle()
    val visibleDestinations = destinations.filter { destination ->
        val feature = when (destination.section) {
            AppSection.ANALYZE -> OptionalFeature.ANALYSIS
            else -> null
        }
        feature == null || featureVisibility.isVisible(feature) || destination.section == section
    }
    val filesHomeVisible by viewModel.filesHomeVisible.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val fileEditState by viewModel.fileEditState.collectAsStateWithLifecycle()
    val signatureLibrary by viewModel.signatureLibrary.collectAsStateWithLifecycle()
    val panelState by viewModel.panel.collectAsStateWithLifecycle()
    val storageRoots by viewModel.roots.collectAsStateWithLifecycle()
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val trashBrowser by viewModel.trashBrowser.collectAsStateWithLifecycle()
    val advancedAccess by viewModel.advancedAccess.collectAsStateWithLifecycle()
    val advancedBrowser by viewModel.advancedBrowser.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val safBrowser by viewModel.safBrowser.collectAsStateWithLifecycle()
    var hasAllFilesAccess by remember { mutableStateOf(hasFullFileAccess(context)) }
    var unlocked by remember(appLockEnabled) { mutableStateOf(!appLockEnabled) }
    var appStopped by remember { mutableStateOf(false) }
    var hiddenOperationIds by remember { mutableStateOf(emptySet<String>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activeOperations = operations.count {
        it.status == OperationStatus.RUNNING || it.status == OperationStatus.PAUSED || it.status == OperationStatus.QUEUED
    }
    val selectionDockVisible = when (section) {
        AppSection.FILES -> panelState.selectedPaths.isNotEmpty()
        else -> false
    }
    val visibleOperation = operations.firstOrNull {
        it.id !in hiddenOperationIds && (it.status == OperationStatus.RUNNING || it.status == OperationStatus.PAUSED)
    } ?: operations.firstOrNull { it.id !in hiddenOperationIds && it.status == OperationStatus.QUEUED }
    LaunchedEffect(activeOperations) {
        if (activeOperations == 0) hiddenOperationIds = emptySet()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val systemBackAction = BackNavigationRules.decide(
        previewOpen = preview != null,
        section = section,
        filesHomeVisible = filesHomeVisible,
        selectedCount = panelState.selectedPaths.size,
        hasBackHistory = panelState.backHistory.isNotEmpty(),
        returnToHomeAtBoundary = panelState.returnToHomeAtBoundary,
        hasParent = File(panelState.path).parentFile != null &&
            !sameNormalizedPath(Environment.getExternalStorageDirectory().absolutePath, panelState.path) &&
            storageRoots.none { root -> sameNormalizedPath(root.path, panelState.path) },
    )

    BackHandler(
        enabled = (!appLockEnabled || unlocked) && systemBackAction != SystemBackAction.DEFER_TO_SYSTEM,
    ) {
        when (systemBackAction) {
            SystemBackAction.CLOSE_PREVIEW -> viewModel.closePreview()
            SystemBackAction.SHOW_FILES -> viewModel.showFilesHome()
            SystemBackAction.CLEAR_SELECTION -> viewModel.clearSelection()
            SystemBackAction.NAVIGATE_BACK -> viewModel.navigateBack()
            SystemBackAction.NAVIGATE_UP -> viewModel.navigateUp()
            SystemBackAction.DEFER_TO_SYSTEM -> Unit
        }
    }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addSafLocation(uri, uri.lastPathSegment?.substringAfterLast(':').orEmpty())
    }
    val systemFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openExternalUri(uri, context.contentResolver.getType(uri))
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAllFilesAccess = hasFullFileAccess(context)
        viewModel.refreshPermissionDependentState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = hasFullFileAccess(context)
                if (current != hasAllFilesAccess) {
                    hasAllFilesAccess = current
                    viewModel.refreshPermissionDependentState()
                } else {
                    viewModel.refreshStorageRoots()
                }
                if (appStopped && appLockEnabled) unlocked = false
                appStopped = false
            } else if (event == Lifecycle.Event.ON_STOP) {
                appStopped = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = UiTranslator.translate(message.text, interfaceLanguage),
                actionLabel = when (message.action) {
                    UiMessageAction.UNDO_BATCH_RENAME -> UiTranslator.translate("Atšaukti", interfaceLanguage)
                    null -> null
                },
                duration = if (message.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && message.action == UiMessageAction.UNDO_BATCH_RENAME) {
                viewModel.undoBatchRename()
            }
        }
    }

    LaunchedEffect(incomingViewRequest) {
        incomingViewRequest?.let { request ->
            viewModel.openExternalUri(request.uri, request.mimeType)
            onIncomingViewRequestConsumed()
        }
    }

    CompositionLocalProvider(LocalFeatureVisibility provides featureVisibility) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        com.shovel.filemanager.ui.theme.AppearanceBackground()
        val wideNavigation = maxWidth >= 900.dp
        val railLabelWidth = (80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)).coerceAtMost(maxWidth / 4)
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (selectionDockVisible) 72.dp else 0.dp),
                )
            },
            bottomBar = {
                Column {
                    if (preview == null && (!appLockEnabled || unlocked)) BackgroundPlaybackBar()
                    if (!wideNavigation) {
                        com.shovel.filemanager.ui.theme.AppearanceContentOn(MaterialTheme.colorScheme.surfaceContainer) {
                        NavigationBar {
                            visibleDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = section == destination.section,
                                    onClick = { viewModel.setSection(destination.section) },
                                    modifier = Modifier.testTag("nav_${destination.section.name.lowercase()}"),
                                    icon = {
                                        Box(Modifier.testTag("nav_icon_${destination.section.name.lowercase()}")) {
                                            DestinationIcon(destination.icon, activeOperations.takeIf { destination.section == AppSection.OPERATIONS })
                                        }
                                    },
                                    label = { NavigationLabel(destination.label) },
                                )
                            }
                        }
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (wideNavigation) {
                    com.shovel.filemanager.ui.theme.AppearanceContentOn(MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationRail {
                        visibleDestinations.forEach { destination ->
                            NavigationRailItem(
                                selected = section == destination.section,
                                onClick = { viewModel.setSection(destination.section) },
                                modifier = Modifier.testTag("nav_${destination.section.name.lowercase()}"),
                                icon = {
                                    Box(Modifier.testTag("nav_icon_${destination.section.name.lowercase()}")) {
                                        DestinationIcon(destination.icon, activeOperations.takeIf { destination.section == AppSection.OPERATIONS })
                                    }
                                },
                                label = { NavigationLabel(destination.label, Modifier.width(railLabelWidth)) },
                            )
                        }
                    }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (section) {
                        AppSection.FILES -> FilesScreen(
                            viewModel = viewModel,
                            contentPadding = padding,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onRequestAllFilesAccess = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                    context.startActivity(intent)
                                } else {
                                    legacyStorageLauncher.launch(
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                    )
                                }
                            },
                            onAddSafLocation = { safLauncher.launch(null) },
                            onOpenSystemFiles = { systemFilesLauncher.launch(arrayOf("*/*")) },
                        )
                        AppSection.ANALYZE -> AnalyzeScreen(viewModel, padding)
                        AppSection.OPERATIONS -> OperationsScreen(viewModel, padding)
                        AppSection.TOOLS -> ToolsScreen(
                            viewModel = viewModel,
                            contentPadding = padding,
                            onAddSafLocation = { safLauncher.launch(null) },
                            onToggleAppLock = { enabled ->
                                if (enabled) {
                                    authenticate(
                                        context = context,
                                        title = UiTranslator.translate("Įjungti Shovel užraktą", interfaceLanguage),
                                        onSuccess = { viewModel.setAppLockEnabled(true); unlocked = true },
                                        onError = { text ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    UiTranslator.translate(text, interfaceLanguage),
                                                )
                                            }
                                        },
                                    )
                                } else {
                                    authenticate(
                                        context = context,
                                        title = UiTranslator.translate("Išjungti Shovel užraktą", interfaceLanguage),
                                        onSuccess = { viewModel.setAppLockEnabled(false); unlocked = true },
                                        onError = { text ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    UiTranslator.translate(text, interfaceLanguage),
                                                )
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (trashBrowser.open) {
        TrashBrowserDialog(
            state = trashBrowser,
            itemCount = trashItems.size,
            viewModel = viewModel,
        )
    }

    if (safBrowser.location != null) {
        SafBrowserDialog(
            state = safBrowser,
            selectedLocalPath = panelState.selectedPaths.singleOrNull(),
            viewModel = viewModel,
            onDismiss = viewModel::closeSafBrowser,
        )
    }

    AdvancedStorageBrowserDialog(
        state = advancedBrowser,
        access = advancedAccess,
        clipboard = clipboard,
        viewModel = viewModel,
    )

    preview?.let { target ->
        val archiveDisplayDefaults = remember(target) { viewModel.currentDirectoryDisplayDefaults() }
        FilePreviewDialog(
            target = target,
            editState = fileEditState,
            signatureLibrary = signatureLibrary,
            archiveDisplayDefaults = archiveDisplayDefaults,
            onApplyArchiveDisplayToAll = viewModel::applyDirectoryDisplaySettingsToAll,
            onClose = viewModel::closePreview,
            onDelete = (viewModel::deletePreviewTarget).takeIf { viewModel.canDeletePreview(target) },
            onPrepareEdit = viewModel::prepareFileEdit,
            onApplyPdfSignature = viewModel::applyPdfVisualSignature,
            onRefreshSignatureLibrary = viewModel::refreshSignatureLibrary,
            onSaveSignature = viewModel::saveSignature,
            onDeleteSavedSignature = viewModel::deleteSavedSignature,
            onEditTextChanged = viewModel::updateEditText,
            onEditEncodingChanged = viewModel::updateEditEncoding,
            onEditLineEndingChanged = viewModel::updateEditLineEnding,
            onSaveEdit = viewModel::saveFileEdit,
            onSaveEditAs = viewModel::saveFileEditAs,
            onSaveEditAsLocal = { directory, name -> viewModel.saveFileEditAsLocal(directory, name) },
            onResolveSaveAsConflict = viewModel::resolveFileEditSaveAsConflict,
            onDismissSaveAsConflict = viewModel::dismissFileEditSaveAsConflict,
            initialLocalSavePath = panelState.path,
            loadLocalSaveDirectory = viewModel::listLocalDirectoryForUpload,
            onExternalEditorReturned = viewModel::refreshFileEditAfterExternalEditor,
            onDismissEditConflict = viewModel::dismissFileEditConflict,
            onKeepEditing = viewModel::keepEditing,
            onDiscardEditAndClose = viewModel::discardFileEditAndClose,
            onCopyArchiveEntry = viewModel::copyArchiveEntryToSet,
            onCopyArchiveEntries = viewModel::copyArchiveEntriesToSet,
            onOpenArchiveEntry = viewModel::openArchiveEntry,
            onShareArchiveEntries = viewModel::shareArchiveEntries,
            onExtractArchiveEntries = viewModel::extractArchiveEntries,
            onRenameArchiveEntry = viewModel::renameArchiveEntry,
            onDeleteArchiveEntries = viewModel::deleteArchiveEntries,
            onMoveArchiveEntries = viewModel::moveArchiveEntriesOut,
            onRefreshArchive = viewModel::refreshArchivePreview,
            loadArchiveThumbnail = viewModel::materializeArchiveThumbnail,
            onExtract = { file, destination, password -> viewModel.extractArchive(file, destination, password) },
            canNavigateMedia = viewModel.canNavigatePreviewMedia(target),
            backgroundPlaylist = viewModel.backgroundMediaPlaylist(target),
            onPreviousMedia = { viewModel.navigatePreviewMedia(-1) },
            onNextMedia = { viewModel.navigatePreviewMedia(1) },
        )
    }
    val archivePasswordRequest by viewModel.archivePasswordRequest.collectAsStateWithLifecycle()
    archivePasswordRequest?.let { entry ->
        ArchivePasswordDialog(
            archiveName = entry.name,
            onConfirm = viewModel::submitArchivePassword,
            onDismiss = viewModel::dismissArchivePasswordRequest,
        )
    }

    BatchRenameDialog(viewModel)

    visibleOperation?.let { operation ->
        OperationProgressDialog(
            operation = operation,
            onCancel = { viewModel.cancelOperation(operation.id) },
            onPause = { viewModel.pauseOperation(operation.id) },
            onResume = { viewModel.resumeOperation(operation.id) },
            onHide = {
                hiddenOperationIds = hiddenOperationIds + operation.id
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }

    if (appLockEnabled && !unlocked) {
        AppLockOverlay(
            onUnlock = {
                authenticate(
                    context = context,
                    title = UiTranslator.translate("Atrakinti Shovel", interfaceLanguage),
                    onSuccess = { unlocked = true },
                    onError = { text ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                UiTranslator.translate(text, interfaceLanguage),
                            )
                        }
                    },
                )
            },
        )
    }
    }
}

private fun hasFullFileAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

private fun sameNormalizedPath(first: String, second: String): Boolean = runCatching {
    File(first).absoluteFile.toPath().normalize() == File(second).absoluteFile.toPath().normalize()
}.getOrElse {
    File(first).absolutePath == File(second).absolutePath
}

@Composable
internal fun AppLockOverlay(onUnlock: () -> Unit, onCancel: (() -> Unit)? = null, message: String? = null) {
    ShovelDialog(
        onDismissRequest = { onCancel?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = onCancel != null, dismissOnClickOutside = false),
    ) {
        com.shovel.filemanager.ui.theme.AppearancePage(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(84.dp), tint = MaterialTheme.colorScheme.primary)
                LText("Shovel užrakinta", style = MaterialTheme.typography.headlineSmall)
                message?.let { LText(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                Button(onClick = onUnlock, modifier = Modifier.padding(top = 18.dp)) { LText("Atrakinti") }
                onCancel?.let { TextButton(onClick = it) { LText("Atšaukti") } }
            }
        }
    }
}

internal fun authenticate(
    context: android.content.Context,
    title: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val activity = context as? FragmentActivity ?: run {
        onError("Biometrinio lango atidaryti nepavyko")
        return
    }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val available = BiometricManager.from(context).canAuthenticate(authenticators)
    if (available != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Įrenginyje nenustatyta tinkama biometrija arba ekrano užraktas")
        return
    }
    val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(UiTranslator.translate("Patvirtinkite tapatybę Android sistemos lange", context.resources.configuration.locales[0].language))
            .setAllowedAuthenticators(authenticators)
            .build(),
    )
}

@Composable
private fun NavigationLabel(label: String, modifier: Modifier = Modifier) {
    val language = LocalConfiguration.current.locales[0].language
    val labels = remember(language) { destinations.map { UiTranslator.translate(it.label, language) } }
    val density = LocalDensity.current
    val style = LocalTextStyle.current.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        textAlign = TextAlign.Center,
    )

    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = with(density) { (maxWidth - 4.dp).roundToPx().coerceAtLeast(1) }
        // Reserve the tallest translated label for every item. Icons keep one
        // baseline even when only one label wraps; no font scaling is disabled.
        val height = remember(labels, style, width, density) {
            labels.maxOf { measurer.measure(it, style, constraints = Constraints(maxWidth = width)).size.height }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(with(density) { height.toDp() }),
            contentAlignment = Alignment.TopCenter,
        ) {
            LText(label, modifier = Modifier.fillMaxWidth(), style = style)
        }
    }
}

@Composable
private fun DestinationIcon(icon: ImageVector, badgeCount: Int?) {
    if (badgeCount != null && badgeCount > 0) {
        BadgedBox(badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }) {
            Icon(icon, contentDescription = null)
        }
    } else {
        Icon(icon, contentDescription = null)
    }
}
