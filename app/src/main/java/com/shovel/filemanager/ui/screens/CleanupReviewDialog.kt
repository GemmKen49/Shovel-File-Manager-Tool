package com.shovel.filemanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Visibility
import com.shovel.filemanager.ui.theme.ShovelAlertDialog as AlertDialog
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import com.shovel.filemanager.ui.theme.ShovelCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import com.shovel.filemanager.ui.theme.ShovelFilterChip as FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.shovel.filemanager.ui.theme.ShovelSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.shovel.filemanager.ui.theme.ShovelDialog
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.model.DuplicateGroup
import com.shovel.filemanager.model.DirectoryContentUsage
import com.shovel.filemanager.model.DirectoryContentsUsage
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.cleanup.OldMediaRules
import com.shovel.filemanager.model.SimilarImageGroup
import com.shovel.filemanager.model.StorageAnalysis
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.uiText
import com.shovel.filemanager.ui.components.LocalFileVisual
import com.shovel.filemanager.ui.components.DeleteConfirmationDialog
import com.shovel.filemanager.ui.components.LazyListFastScroller
import com.shovel.filemanager.data.FileSelectionSummary
import com.shovel.filemanager.data.FileSelectionInfoScanner
import java.io.File
import java.text.NumberFormat

private const val MAX_CLEANUP_FOLDER_CACHE = 8
private const val MAX_SMART_DUPLICATE_SELECTION = 10_000

internal enum class CleanupCategory {
    TYPE_USAGE,
    LARGEST_FOLDERS,
    LARGE,
    OLDEST,
    OLD_MEDIA,
    EMPTY_FOLDERS,
    DUPLICATES,
    PACKAGES,
    SIMILAR_IMAGES,
}

private data class CleanupCandidate(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val directory: Boolean = false,
    val groupLabel: String? = null,
    val entry: FileEntry,
)

internal object DuplicateCleanupSelectionRules {
    fun selectCopies(currentSelection: Set<String>, groups: List<DuplicateGroup>): Set<String> {
        val duplicatePaths = groups.asSequence()
            .flatMap { it.paths.asSequence() }
            .filter(String::isNotBlank)
            .distinctBy(::cleanupPathKey)
            .toSet()
        return (currentSelection - duplicatePaths) + copiesToSelect(groups)
    }

    fun copiesToSelect(groups: List<DuplicateGroup>): Set<String> {
        val selected = LinkedHashSet<String>()
        groups.forEach { group ->
            val distinctPaths = group.paths
                .filter(String::isNotBlank)
                .distinctBy(::cleanupPathKey)
                .sortedBy(::cleanupPathKey)
            distinctPaths.drop(1).forEach { path ->
                if (selected.size >= MAX_SMART_DUPLICATE_SELECTION) return selected
                selected += path
            }
        }
        return selected
    }
}

@Composable
internal fun CleanupReviewDialog(
    analysis: StorageAnalysis,
    duplicates: List<DuplicateGroup>,
    similarImages: List<SimilarImageGroup>,
    similarImagesRunning: Boolean,
    similarImagesAnalyzed: Boolean,
    similarImagesError: String?,
    initialCategory: CleanupCategory,
    analysisRootPaths: List<String>,
    onAnalyzeSimilarImages: () -> Unit,
    onMoveToTrash: (Set<String>) -> Unit,
    onMoveAllToTrash: ((Set<String>) -> Unit)? = null,
    loadSelectionInfo: suspend (Collection<String>) -> Result<FileSelectionSummary> = { paths ->
        runCatching { FileSelectionInfoScanner().scan(paths) }
    },
    onLoadFolder: suspend (String) -> Result<DirectoryContentsUsage>,
    onOpenFile: (FileEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var selected by remember(analysis) { mutableStateOf(emptySet<String>()) }
    var confirmTrash by remember { mutableStateOf(false) }
    var keepOneCopy by remember(selected) { mutableStateOf(true) }
    var folderStack by remember(analysis) { mutableStateOf(emptyList<String>()) }
    var folderListing by remember(analysis) { mutableStateOf<DirectoryContentsUsage?>(null) }
    var folderLoading by remember(analysis) { mutableStateOf(false) }
    var folderError by remember(analysis) { mutableStateOf<String?>(null) }
    var folderRetry by remember(analysis) { mutableIntStateOf(0) }
    var folderListingCache by remember(analysis) { mutableStateOf(emptyMap<String, DirectoryContentsUsage>()) }
    val currentFolderPath = folderStack.lastOrNull()
    val categoryCandidates = remember(category, analysis, duplicates, similarImages, analysisRootPaths) {
        cleanupCandidates(category, analysis, duplicates, similarImages, analysisRootPaths)
    }
    val initialKnownCandidates = remember(analysis, duplicates, similarImages, analysisRootPaths) {
        allCleanupCandidates(analysis, duplicates, similarImages, analysisRootPaths)
            .associateBy(CleanupCandidate::path)
    }
    var knownCandidates by remember(initialKnownCandidates) { mutableStateOf(initialKnownCandidates) }
    val folderCandidates = remember(folderListing) {
        folderListing?.entries?.map(DirectoryContentUsage::toCleanupCandidate).orEmpty()
    }
    val candidates = if (currentFolderPath == null) categoryCandidates else folderCandidates
    val candidateTotalBytes = if (currentFolderPath == null) analysis.totalBytes else folderListing?.totalBytes ?: 0L
    val duplicateCopies = remember(duplicates) { DuplicateCleanupSelectionRules.copiesToSelect(duplicates) }
    val selectedBytes = remember(selected, knownCandidates) {
        selected.sumOf { path -> knownCandidates[path]?.sizeBytes ?: 0L }
    }
    val navigateBack: () -> Unit = {
        if (folderStack.isNotEmpty()) {
            folderStack = folderStack.dropLast(1)
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(currentFolderPath, folderRetry) {
        val path = currentFolderPath
        if (path == null) {
            folderListing = null
            folderLoading = false
            folderError = null
            return@LaunchedEffect
        }
        folderListingCache[path]?.let { cached ->
            folderListing = cached
            folderLoading = false
            folderError = null
            return@LaunchedEffect
        }
        folderListing = null
        folderLoading = true
        folderError = null
        onLoadFolder(path).fold(
            onSuccess = { listing ->
                folderListing = listing
                val updatedCache = cacheCleanupFolderListing(folderListingCache, listing)
                folderListingCache = updatedCache
                knownCandidates = rebuildKnownCleanupCandidates(
                    initial = initialKnownCandidates,
                    previous = knownCandidates,
                    selected = selected,
                    cache = updatedCache,
                )
                folderLoading = false
            },
            onFailure = { error ->
                folderError = error.message ?: "Could not load the folder contents"
                folderLoading = false
            },
        )
    }

    ShovelDialog(
        onDismissRequest = navigateBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        com.shovel.filemanager.ui.theme.AppearancePage(
            modifier = Modifier.fillMaxSize().testTag("cleanup_review_dialog"),
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = navigateBack, modifier = Modifier.testTag("cleanup_back")) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = uiText(if (currentFolderPath == null) "Close" else "Back"),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (currentFolderPath == null) {
                            LText("Safe cleanup review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            LText("Nothing is selected or deleted automatically", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LText("Folder contents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                currentFolderPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (currentFolderPath == null && category == CleanupCategory.EMPTY_FOLDERS) {
                        val emptyFolderPaths = categoryCandidates.mapTo(linkedSetOf(), CleanupCandidate::path)
                        val allSelected = emptyFolderPaths.isNotEmpty() && emptyFolderPaths.all(selected::contains)
                        IconButton(
                            onClick = {
                                selected = if (allSelected) selected - emptyFolderPaths else selected + emptyFolderPaths
                            },
                            enabled = emptyFolderPaths.isNotEmpty(),
                            modifier = Modifier.testTag("cleanup_select_all_empty_folders"),
                        ) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = uiText(if (allSelected) "Deselect all" else "Select all"),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Icon(
                            if (currentFolderPath == null) Icons.Rounded.DeleteSweep else Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (currentFolderPath == null && category == CleanupCategory.DUPLICATES) {
                        IconButton(
                            onClick = {
                                selected = DuplicateCleanupSelectionRules.selectCopies(selected, duplicates)
                            },
                            enabled = duplicateCopies.isNotEmpty(),
                            modifier = Modifier.testTag("cleanup_select_duplicate_copies"),
                        ) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = uiText("Select duplicate copies"),
                            )
                        }
                    }
                }
                if (currentFolderPath == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        CleanupCategory.entries.forEach { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = {
                                    category = item
                                    folderStack = emptyList()
                                },
                                label = { LText(cleanupCategoryLabel(item)) },
                                leadingIcon = { Icon(cleanupCategoryIcon(item), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                if (currentFolderPath != null || category != CleanupCategory.TYPE_USAGE) {
                    Card(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LText("Selected: ${selected.size}", fontWeight = FontWeight.SemiBold)
                            LText("Can free up: ${FileSystemRules.humanBytes(selectedBytes)}", style = MaterialTheme.typography.bodySmall)
                            LText("Selected items will be moved to Shovel's recoverable trash.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (currentFolderPath != null) {
                    when {
                        folderLoading -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator()
                                    LText("Calculating folder sizes…")
                                }
                            }
                        }
                        folderError != null -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    LText("Could not load the folder contents", fontWeight = FontWeight.SemiBold)
                                    folderError?.let { LText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                    OutlinedButton(
                                        onClick = {
                                            val updatedCache = folderListingCache - currentFolderPath
                                            folderListingCache = updatedCache
                                            knownCandidates = rebuildKnownCleanupCandidates(
                                                initial = initialKnownCandidates,
                                                previous = knownCandidates,
                                                selected = selected,
                                                cache = updatedCache,
                                            )
                                            folderRetry += 1
                                        },
                                    ) { LText("Try again") }
                                }
                            }
                        }
                        folderListing != null && candidates.isEmpty() -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LText("There are no files in this folder.")
                            }
                        }
                        folderListing != null -> {
                            Column(modifier = Modifier.weight(1f)) {
                                if (folderListing?.truncated == true) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    ) {
                                        LText(
                                            "Some contents or folder sizes are partial because the safe scan limit was reached.",
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                CleanupCandidateList(
                                    candidates = candidates,
                                    totalBytes = candidateTotalBytes,
                                    selected = selected,
                                    onToggle = { candidate ->
                                        selected = toggleCleanupSelection(selected, candidate, knownCandidates)
                                    },
                                    onOpen = { candidate ->
                                        if (candidate.directory) folderStack = folderStack + candidate.path
                                        else onOpenFile(candidate.entry)
                                    },
                                    modifier = Modifier.weight(1f),
                                    testTag = "cleanup_folder_browser",
                                )
                            }
                        }
                    }
                } else if (category == CleanupCategory.TYPE_USAGE) {
                    val maximum = analysis.typeUsage.maxOfOrNull { it.sizeBytes }?.coerceAtLeast(1L) ?: 1L
                    val typeUsageListState = rememberLazyListState()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag("analysis_type_usage"), state = typeUsageListState) {
                            items(analysis.typeUsage, key = { it.kind }) { usage ->
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        LText(cleanupKindLabel(usage.kind), fontWeight = FontWeight.SemiBold)
                                        LText("${FileSystemRules.humanBytes(usage.sizeBytes)} · ${usage.fileCount}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    LinearProgressIndicator(
                                        progress = { usage.sizeBytes.toFloat() / maximum.toFloat() },
                                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                        LazyListFastScroller(typeUsageListState)
                    }
                } else if (category == CleanupCategory.SIMILAR_IMAGES && !similarImagesAnalyzed) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (similarImagesRunning) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator()
                                LText("Comparing photos…")
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LText("Similarity is checked on the device. It can take time, so analysis starts only when requested.")
                                OutlinedButton(onClick = onAnalyzeSimilarImages) { LText("Find similar photos") }
                            }
                        }
                    }
                } else if (candidates.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LText(if (category == CleanupCategory.SIMILAR_IMAGES) "No groups of similar photos were found" else "No items were found in this category")
                            similarImagesError?.let { LText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } else {
                    CleanupCandidateList(
                        candidates = candidates,
                        totalBytes = candidateTotalBytes,
                        selected = selected,
                        onToggle = { candidate ->
                            selected = toggleCleanupSelection(selected, candidate, knownCandidates)
                        },
                        onOpen = { candidate ->
                            if (candidate.directory) folderStack = folderStack + candidate.path
                            else onOpenFile(candidate.entry)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "cleanup_candidates",
                    )
                }

                if (currentFolderPath != null || category != CleanupCategory.TYPE_USAGE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { LText("Deselect all") }
                        Button(
                            onClick = { confirmTrash = true },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.testTag("cleanup_move_selected"),
                        ) {
                            LText("Move to trash (${selected.size})")
                        }
                    }
                }
            }
        }
    }

    if (confirmTrash) {
        val selectedSnapshot = selected
        val protectedSelection = remember(selectedSnapshot, duplicates, similarImages) {
            com.shovel.filemanager.operations.CleanupCopyProtection.select(selectedSnapshot.map(::File),
                duplicates.map { it.paths } + similarImages.map { group -> group.files.map { it.absolutePath } })
        }
        DeleteConfirmationDialog(
            names = selectedSnapshot.map { File(it).name }, permanent = false,
            title = "Move the selected items to trash?",
            onDismiss = { confirmTrash = false },
            onConfirm = {
                confirmTrash = false
                if (!keepOneCopy && onMoveAllToTrash != null) onMoveAllToTrash(selectedSnapshot)
                else onMoveToTrash(selectedSnapshot)
                selected = emptySet()
            },
            loadSummary = { loadSelectionInfo(selectedSnapshot) },
            explanation = "Items can later be restored or permanently deleted.",
            confirmTestTag = "cleanup_confirm_move",
            extraContent = {
                if (protectedSelection.retained.isNotEmpty() && onMoveAllToTrash != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = keepOneCopy, onCheckedChange = { keepOneCopy = it },
                            modifier = Modifier.testTag("cleanup_keep_one_copy"))
                        LText("Keep at least one file from every identical or similar group",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        )
    }
}

@Composable
private fun CleanupCandidateList(
    candidates: List<CleanupCandidate>,
    totalBytes: Long,
    selected: Set<String>,
    onToggle: (CleanupCandidate) -> Unit,
    onOpen: (CleanupCandidate) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(testTag),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(candidates, key = CleanupCandidate::path) { candidate ->
                CleanupCandidateRow(
                    candidate = candidate,
                    totalBytes = totalBytes,
                    selected = candidate.path in selected,
                    onToggle = { onToggle(candidate) },
                    onOpen = { onOpen(candidate) },
                )
            }
        }
        LazyListFastScroller(listState)
    }
}

@Composable
private fun CleanupCandidateRow(
    candidate: CleanupCandidate,
    totalBytes: Long,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 2 }
    }
    val percentage = if (totalBytes <= 0L) 0.0 else candidate.sizeBytes.toDouble() / totalBytes.toDouble()
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).testTag("cleanup_candidate_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("cleanup_candidate_checkbox"),
            )
            LocalFileVisual(
                entry = candidate.entry,
                targetWidth = 42.dp,
                targetHeight = 42.dp,
                showThumbnails = true,
                modifier = Modifier.size(42.dp).testTag("cleanup_candidate_visual"),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(candidate.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                candidate.groupLabel?.let { LText(it, style = MaterialTheme.typography.labelSmall) }
                if (candidate.sizeBytes > 0L) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(FileSystemRules.humanBytes(candidate.sizeBytes), style = MaterialTheme.typography.labelSmall)
                        Text(percentFormat.format(percentage), style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { percentage.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            IconButton(onClick = onOpen) {
                Icon(
                    if (candidate.directory) Icons.Rounded.FolderOpen else Icons.Rounded.Visibility,
                    contentDescription = uiText(if (candidate.directory) "Open folder" else "Preview here"),
                )
            }
        }
    }
}

private fun cleanupCandidates(
    category: CleanupCategory,
    analysis: StorageAnalysis,
    duplicates: List<DuplicateGroup>,
    similarImages: List<SimilarImageGroup>,
    analysisRootPaths: List<String>,
): List<CleanupCandidate> = when (category) {
    CleanupCategory.TYPE_USAGE -> emptyList()
    CleanupCategory.LARGEST_FOLDERS -> analysis.largestDirectories
        .filterNot { usage -> analysisRootPaths.any { root -> cleanupPathKey(usage.path) == cleanupPathKey(root) } }
        .map { usage ->
            CleanupCandidate(
                path = usage.path,
                name = File(usage.path).name.ifBlank { usage.path },
                sizeBytes = usage.sizeBytes,
                directory = true,
                groupLabel = "${usage.fileCount} files",
                entry = cleanupEntry(usage.path, usage.sizeBytes, directory = true),
            )
        }
    CleanupCategory.LARGE -> analysis.largestFiles.map(FileEntry::toCleanupCandidate)
    CleanupCategory.OLDEST -> analysis.oldestFiles.map(FileEntry::toCleanupCandidate)
    CleanupCategory.OLD_MEDIA -> analysis.oldMediaFiles.map(FileEntry::toCleanupCandidate)
    CleanupCategory.PACKAGES -> analysis.installerAndArchiveFiles.map(FileEntry::toCleanupCandidate)
    CleanupCategory.DUPLICATES -> duplicates.flatMapIndexed { index, group ->
        group.paths.map { path ->
            CleanupCandidate(
                path = path,
                name = File(path).name,
                sizeBytes = group.sizeBytes,
                groupLabel = "Identical files group ${index + 1}",
                entry = cleanupEntry(path, group.sizeBytes),
            )
        }
    }
    CleanupCategory.EMPTY_FOLDERS -> analysis.emptyDirectories.map { path ->
        CleanupCandidate(
            path = path,
            name = File(path).name.ifBlank { path },
            sizeBytes = 0L,
            directory = true,
            entry = cleanupEntry(path, 0L, directory = true),
        )
    }
    CleanupCategory.SIMILAR_IMAGES -> similarImages.flatMapIndexed { index, group ->
        group.files.map { it.toCleanupCandidate(groupLabel = "Similar photos group ${index + 1}") }
    }
}.distinctBy(CleanupCandidate::path)

private fun allCleanupCandidates(
    analysis: StorageAnalysis,
    duplicates: List<DuplicateGroup>,
    similarImages: List<SimilarImageGroup>,
    analysisRootPaths: List<String>,
): List<CleanupCandidate> = CleanupCategory.entries.flatMap {
    cleanupCandidates(it, analysis, duplicates, similarImages, analysisRootPaths)
}

private fun cleanupPathKey(value: String): String {
    val path = value.replace('\\', '/')
    return if (path == "/") path else path.trimEnd('/')
}

private fun FileEntry.toCleanupCandidate(groupLabel: String? = null) = CleanupCandidate(
    path = absolutePath,
    name = name,
    sizeBytes = sizeBytes,
    directory = isDirectory,
    groupLabel = groupLabel,
    entry = this,
)

private fun DirectoryContentUsage.toCleanupCandidate() = CleanupCandidate(
    path = entry.absolutePath,
    name = entry.name,
    sizeBytes = entry.sizeBytes,
    directory = entry.isDirectory,
    groupLabel = if (entry.isDirectory) {
        if (entry.metadataComplete) "$fileCount files" else "At least $fileCount files"
    } else {
        null
    },
    entry = entry,
)

private fun cleanupEntry(path: String, sizeBytes: Long, directory: Boolean = false): FileEntry {
    val name = File(path).name.ifBlank { path }
    return FileEntry(
        absolutePath = path,
        name = name,
        kind = FileSystemRules.detectKind(name, mimeType = null, isDirectory = directory),
        sizeBytes = sizeBytes,
        modifiedAtMillis = 0L,
        isHidden = name.startsWith('.'),
        isReadable = true,
        isWritable = false,
        metadataComplete = false,
    )
}

private fun toggleCleanupSelection(
    selected: Set<String>,
    candidate: CleanupCandidate,
    knownCandidates: Map<String, CleanupCandidate>,
): Set<String> {
    if (candidate.path in selected) return selected - candidate.path
    val withoutOverlaps = selected.filterNot { selectedPath ->
        val selectedCandidate = knownCandidates[selectedPath]
        (selectedCandidate?.directory == true && cleanupPathContains(selectedPath, candidate.path)) ||
            (candidate.directory && cleanupPathContains(candidate.path, selectedPath))
    }.toSet()
    return withoutOverlaps + candidate.path
}

private fun cacheCleanupFolderListing(
    cache: Map<String, DirectoryContentsUsage>,
    listing: DirectoryContentsUsage,
): Map<String, DirectoryContentsUsage> {
    val bounded = LinkedHashMap(cache)
    bounded.remove(listing.directoryPath)
    bounded[listing.directoryPath] = listing
    while (bounded.size > MAX_CLEANUP_FOLDER_CACHE) {
        bounded.remove(bounded.keys.first())
    }
    return bounded
}

private fun rebuildKnownCleanupCandidates(
    initial: Map<String, CleanupCandidate>,
    previous: Map<String, CleanupCandidate>,
    selected: Set<String>,
    cache: Map<String, DirectoryContentsUsage>,
): Map<String, CleanupCandidate> = buildMap {
    putAll(initial)
    selected.forEach { path -> previous[path]?.let { candidate -> put(path, candidate) } }
    cache.values.forEach { listing ->
        listing.entries.forEach { usage ->
            val candidate = usage.toCleanupCandidate()
            put(candidate.path, candidate)
        }
    }
}

private fun cleanupPathContains(parent: String, candidate: String): Boolean {
    val normalizedParent = cleanupPathKey(parent)
    val normalizedCandidate = cleanupPathKey(candidate)
    if (normalizedParent == normalizedCandidate) return true
    val prefix = if (normalizedParent == "/") "/" else "$normalizedParent/"
    return normalizedCandidate.startsWith(prefix)
}

private fun cleanupCategoryLabel(category: CleanupCategory): String = when (category) {
    CleanupCategory.TYPE_USAGE -> "File-type distribution"
    CleanupCategory.LARGEST_FOLDERS -> "Largest folders"
    CleanupCategory.LARGE -> "Largest files"
    CleanupCategory.OLDEST -> "Oldest modified files"
    CleanupCategory.OLD_MEDIA -> "Old media and screenshots"
    CleanupCategory.PACKAGES -> "APKs and archives"
    CleanupCategory.DUPLICATES -> "Identical files"
    CleanupCategory.EMPTY_FOLDERS -> "Empty folders"
    CleanupCategory.SIMILAR_IMAGES -> "Similar photos"
}

private fun cleanupCategoryIcon(category: CleanupCategory): ImageVector = when (category) {
    CleanupCategory.TYPE_USAGE -> Icons.AutoMirrored.Rounded.InsertDriveFile
    CleanupCategory.LARGEST_FOLDERS -> Icons.Rounded.Folder
    CleanupCategory.LARGE -> Icons.AutoMirrored.Rounded.InsertDriveFile
    CleanupCategory.OLDEST -> Icons.AutoMirrored.Rounded.InsertDriveFile
    CleanupCategory.OLD_MEDIA -> Icons.Rounded.Image
    CleanupCategory.PACKAGES -> Icons.Rounded.Archive
    CleanupCategory.DUPLICATES -> Icons.Rounded.ContentCopy
    CleanupCategory.EMPTY_FOLDERS -> Icons.Rounded.Folder
    CleanupCategory.SIMILAR_IMAGES -> Icons.Rounded.Image
}

internal fun isOldMediaCleanupCandidate(entry: FileEntry): Boolean {
    return OldMediaRules.isCategoryCandidate(entry)
}

private fun cleanupKindLabel(kind: com.shovel.filemanager.model.EntryKind): String = when (kind) {
    com.shovel.filemanager.model.EntryKind.DIRECTORY -> "Folders"
    com.shovel.filemanager.model.EntryKind.IMAGE -> "Pictures"
    com.shovel.filemanager.model.EntryKind.VIDEO -> "Videos"
    com.shovel.filemanager.model.EntryKind.AUDIO -> "Audio"
    com.shovel.filemanager.model.EntryKind.DOCUMENT -> "Documents"
    com.shovel.filemanager.model.EntryKind.ARCHIVE -> "Archives"
    com.shovel.filemanager.model.EntryKind.APK -> "APK"
    com.shovel.filemanager.model.EntryKind.OTHER -> "Other"
}
