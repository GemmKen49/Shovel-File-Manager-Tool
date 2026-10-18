package com.shovel.filemanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.shovel.filemanager.data.FolderStats
import com.shovel.filemanager.data.itemsText
import com.shovel.filemanager.data.sizeText
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.ui.MainViewModel

internal sealed interface FolderDetailsState {
    object None : FolderDetailsState
    object Loading : FolderDetailsState
    data class Ready(val stats: FolderStats) : FolderDetailsState
}

/** Loads the folder/archive details for one visible row. The work stops by itself when the row scrolls away. */
@Composable
internal fun rememberFolderDetails(
    viewModel: MainViewModel,
    entry: FileEntry,
    includeHidden: Boolean,
    enabled: Boolean,
): FolderDetailsState {
    val relevant = enabled && entry.metadataComplete && (entry.isDirectory || entry.kind == EntryKind.ARCHIVE)
    val state: State<FolderDetailsState> = produceState<FolderDetailsState>(
        initialValue = if (relevant) FolderDetailsState.Loading else FolderDetailsState.None,
        entry.absolutePath,
        entry.modifiedAtMillis,
        entry.sizeBytes,
        includeHidden,
        relevant,
    ) {
        if (!relevant) {
            value = FolderDetailsState.None
            return@produceState
        }
        value = FolderDetailsState.Loading
        val stats = viewModel.folderStats(entry, includeHidden)
        value = if (stats == null) FolderDetailsState.None else FolderDetailsState.Ready(stats)
    }
    return state.value
}

/** The extra text shown under a folder or archive, for example "12 items · 3.4 MB". */
internal fun folderDetailsText(entry: FileEntry, details: FolderDetailsState): String? = when (details) {
    FolderDetailsState.None -> null
    FolderDetailsState.Loading -> "Counting…"
    is FolderDetailsState.Ready -> {
        val size = details.stats.sizeText()
        "${details.stats.itemsText()} · ${if (entry.isDirectory) size else "$size unpacked"}"
    }
}
