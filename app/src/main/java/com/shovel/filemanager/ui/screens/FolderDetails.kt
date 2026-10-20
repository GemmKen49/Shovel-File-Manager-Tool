package com.shovel.filemanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.data.FolderStats
import com.shovel.filemanager.data.itemsText
import com.shovel.filemanager.data.sizeText
import com.shovel.filemanager.model.EntryKind
import com.shovel.filemanager.model.FileEntry
import com.shovel.filemanager.ui.MainViewModel
import kotlinx.coroutines.delay

internal sealed interface FolderDetailsState {
    object None : FolderDetailsState
    object Loading : FolderDetailsState
    data class Ready(val stats: FolderStats) : FolderDetailsState
}

// Rows that only flash past during a fling must not start any file access.
private const val SCAN_START_DELAY_MILLIS = 150L

/**
 * Loads the folder/archive details for one visible row. Known numbers show immediately and are refreshed quietly;
 * the work stops by itself when the row scrolls away.
 */
@Composable
internal fun rememberFolderDetails(
    viewModel: MainViewModel,
    entry: FileEntry,
    includeHidden: Boolean,
    enabled: Boolean,
): FolderDetailsState {
    val relevant = enabled && entry.metadataComplete && (entry.isDirectory || entry.kind == EntryKind.ARCHIVE)
    val state: State<FolderDetailsState> = produceState<FolderDetailsState>(
        initialValue = when {
            !relevant -> FolderDetailsState.None
            else -> viewModel.cachedFolderStats(entry, includeHidden)?.let { FolderDetailsState.Ready(it) }
                ?: FolderDetailsState.Loading
        },
        entry.absolutePath,
        entry.modifiedAtMillis,
        includeHidden,
        relevant,
    ) {
        if (!relevant) {
            value = FolderDetailsState.None
            return@produceState
        }
        delay(SCAN_START_DELAY_MILLIS)
        val final = viewModel.loadFolderStats(entry, includeHidden) { stats -> value = FolderDetailsState.Ready(stats) }
        if (final == null) value = FolderDetailsState.None
    }
    return state.value
}

/** The extra text shown under a folder or archive, for example "12 items · 3.4 MB". */
internal fun folderDetailsText(entry: FileEntry, details: FolderDetailsState): String? = when (details) {
    FolderDetailsState.None -> null
    FolderDetailsState.Loading -> "Counting…"
    is FolderDetailsState.Ready -> {
        val stats = details.stats
        when {
            !stats.sizeAvailable -> stats.itemsText()
            // Still adding up: show the running total with an ellipsis.
            !stats.sizeReady -> "${stats.itemsText()} · ${if (stats.totalBytes > 0L) FileSystemRules.humanBytes(stats.totalBytes) + "…" else "…"}"
            else -> {
                val size = stats.sizeText()
                "${stats.itemsText()} · ${if (entry.isDirectory) size else "$size unpacked"}"
            }
        }
    }
}
