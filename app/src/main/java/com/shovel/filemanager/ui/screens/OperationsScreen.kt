package com.shovel.filemanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.operations.LogLevel
import com.shovel.filemanager.operations.OperationLogEvent
import com.shovel.filemanager.operations.OperationLogStore
import com.shovel.filemanager.operations.OperationLogSummary
import com.shovel.filemanager.operations.OperationSnapshot
import com.shovel.filemanager.operations.OperationStatus
import com.shovel.filemanager.ui.MainViewModel
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.uiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_VISIBLE_LOG_LINES = 300

/** One row of the page, built from the saved log summary and, while it runs, the live snapshot. */
private data class OperationRow(
    val id: String,
    val title: String,
    val status: OperationStatus,
    val sortTime: Long,
    val startedAtMillis: Long?,
    val finishedAtMillis: Long?,
    val completedItems: Int,
    val totalItems: Int?,
    val completedBytes: Long,
    val totalBytes: Long?,
    val errorCount: Int,
    val message: String?,
    val currentName: String?,
    val snapshot: OperationSnapshot?,
) {
    val active: Boolean
        get() = status == OperationStatus.RUNNING || status == OperationStatus.PAUSED || status == OperationStatus.QUEUED
}

private fun buildRows(summaries: List<OperationLogSummary>, live: List<OperationSnapshot>): List<OperationRow> {
    val liveById = live.associateBy(OperationSnapshot::id)
    val fromLogs = summaries.map { summary ->
        val snapshot = liveById[summary.id]
        OperationRow(
            id = summary.id,
            title = summary.title,
            status = snapshot?.status ?: summary.status,
            sortTime = summary.queuedAtMillis,
            startedAtMillis = snapshot?.startedAtMillis ?: summary.startedAtMillis,
            finishedAtMillis = snapshot?.finishedAtMillis ?: summary.finishedAtMillis,
            completedItems = snapshot?.completedItems ?: summary.completedItems,
            totalItems = snapshot?.totalItems ?: summary.totalItems,
            completedBytes = snapshot?.completedBytes ?: summary.completedBytes,
            totalBytes = snapshot?.totalBytes ?: summary.totalBytes,
            errorCount = snapshot?.errorCount ?: summary.errorCount,
            message = snapshot?.message ?: summary.message,
            currentName = snapshot?.currentName,
            snapshot = snapshot,
        )
    }
    val known = summaries.mapTo(HashSet()) { it.id }
    // Operations restored from an earlier version of the app may have no saved log yet.
    val withoutLog = live.filter { it.id !in known }.map { snapshot ->
        OperationRow(
            id = snapshot.id,
            title = snapshot.title,
            status = snapshot.status,
            sortTime = snapshot.startedAtMillis ?: snapshot.finishedAtMillis ?: 0L,
            startedAtMillis = snapshot.startedAtMillis,
            finishedAtMillis = snapshot.finishedAtMillis,
            completedItems = snapshot.completedItems,
            totalItems = snapshot.totalItems,
            completedBytes = snapshot.completedBytes,
            totalBytes = snapshot.totalBytes,
            errorCount = snapshot.errorCount,
            message = snapshot.message,
            currentName = snapshot.currentName,
            snapshot = snapshot,
        )
    }
    return (fromLogs + withoutLog).sortedWith(
        compareByDescending<OperationRow> { it.active }.thenByDescending { it.sortTime },
    )
}

@Composable
fun OperationsScreen(
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summaries by viewModel.operationLogs.collectAsStateWithLifecycle()
    val live by viewModel.operations.collectAsStateWithLifecycle()
    val debug by viewModel.operationLogDebugMode.collectAsStateWithLifecycle()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val rows = remember(summaries, live) { buildRows(summaries, live) }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).testTag("operations_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("operations_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    LText("Operations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    LText(
                        if (rows.isEmpty()) "Nothing has run yet" else "${rows.size} saved · newest first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                LText("Full debug logging", fontWeight = FontWeight.SemiBold)
                                LText(
                                    if (debug) {
                                        "Recording every file and technical error details for new operations. Turn off for simple logs."
                                    } else {
                                        "Simple logs: what started, what went wrong and the result. Turn on to also record every file."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = debug,
                                onCheckedChange = viewModel::setOperationLogDebugMode,
                                modifier = Modifier.testTag("operations_debug_switch"),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val report = viewModel.operationLogReport(appInfoLine(context))
                                        copyReport(context, report)
                                    }
                                },
                                enabled = rows.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("operations_copy"),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                LText("Copy", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val report = viewModel.operationLogReport(appInfoLine(context))
                                        shareReport(context, report)
                                    }
                                },
                                enabled = rows.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("operations_share"),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                LText("Share", modifier = Modifier.padding(start = 6.dp))
                            }
                            TextButton(
                                onClick = { confirmClear = true },
                                enabled = summaries.isNotEmpty(),
                                modifier = Modifier.testTag("operations_clear"),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                LText("Clear", modifier = Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                LText("No operations yet", fontWeight = FontWeight.SemiBold)
                                LText(
                                    "Copy, move, delete, archive and trash operations will appear here with a log of what happened.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            } else {
                items(rows, key = OperationRow::id) { row ->
                    OperationLogCard(
                        row = row,
                        debug = debug,
                        expanded = expandedId == row.id,
                        onToggle = { expandedId = if (expandedId == row.id) null else row.id },
                        loadEvents = { viewModel.operationLogEvents(row.id) },
                        onPause = { viewModel.pauseOperation(row.id) },
                        onResume = { viewModel.resumeOperation(row.id) },
                        onCancel = { viewModel.cancelOperation(row.id) },
                        onRetry = { viewModel.retryOperation(row.id) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { LText("Clear operation logs?") },
            text = {
                LText("All saved logs are deleted from this device. Operations that are still running keep their logs.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        expandedId = null
                        viewModel.clearOperationLogs()
                    },
                ) { LText("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { LText("Cancel") } },
        )
    }
}

@Composable
private fun OperationLogCard(
    row: OperationRow,
    debug: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    loadEvents: suspend () -> List<OperationLogEvent>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().testTag("operation_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    LText(row.title, fontWeight = FontWeight.SemiBold)
                    StatusLine(row)
                }
                val snapshot = row.snapshot
                when (row.status) {
                    OperationStatus.RUNNING -> IconButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = uiText("Pause"))
                    }
                    OperationStatus.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = uiText("Resume"))
                    }
                    else -> Unit
                }
                if (row.active && snapshot != null) {
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Cancel, contentDescription = uiText("Cancel")) }
                }
                if (!row.active && snapshot?.retryable == true) {
                    IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = uiText("Try again")) }
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = uiText(if (expanded) "Hide log" else "Show log"),
                )
            }
            val totalBytes = row.totalBytes
            if (row.active) {
                if (totalBytes != null && totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (row.completedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (row.status == OperationStatus.RUNNING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                row.currentName?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            row.message?.takeIf { it.isNotBlank() && row.status != OperationStatus.SUCCEEDED }?.let { message ->
                LText(
                    message,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.status == OperationStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                )
            }
            if (expanded) {
                HorizontalDivider()
                LogLines(row = row, debug = debug, loadEvents = loadEvents)
            }
        }
    }
}

@Composable
private fun StatusLine(row: OperationRow) {
    val color = when (row.status) {
        OperationStatus.FAILED -> MaterialTheme.colorScheme.error
        OperationStatus.COMPLETED_WITH_ERRORS, OperationStatus.INTERRUPTED -> MaterialTheme.colorScheme.tertiary
        OperationStatus.SUCCEEDED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val parts = buildList {
        add(OperationLogStore.statusLabel(row.status).replaceFirstChar { it.uppercase() })
        val time = row.finishedAtMillis ?: row.startedAtMillis ?: row.sortTime.takeIf { it > 0L }
        time?.let { add(OperationLogStore.formatTimestamp(it)) }
        val started = row.startedAtMillis
        val finished = row.finishedAtMillis
        if (started != null && finished != null) add(OperationLogStore.formatDuration(finished - started))
        val items = buildString {
            append(row.completedItems)
            row.totalItems?.let { append("/").append(it) }
            append(" items")
        }
        add(items)
        if (row.completedBytes > 0) add(FileSystemRules.humanBytes(row.completedBytes))
        if (row.errorCount > 0) add("${row.errorCount} errors")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row.status == OperationStatus.FAILED || row.status == OperationStatus.COMPLETED_WITH_ERRORS) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp).padding(end = 0.dp),
            )
        }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(start = if (row.status == OperationStatus.FAILED || row.status == OperationStatus.COMPLETED_WITH_ERRORS) 4.dp else 0.dp),
        )
    }
}

@Composable
private fun LogLines(
    row: OperationRow,
    debug: Boolean,
    loadEvents: suspend () -> List<OperationLogEvent>,
) {
    var events by remember(row.id) { mutableStateOf<List<OperationLogEvent>?>(null) }
    val active = row.active
    LaunchedEffect(row.id, active) {
        events = loadEvents()
        if (active) {
            while (true) {
                delay(1_000)
                events = loadEvents()
            }
        }
    }
    val loaded = events
    if (loaded == null) {
        LText("Loading log…", style = MaterialTheme.typography.bodySmall)
        return
    }
    val hiddenDetails = if (debug) 0 else loaded.count { it.level == LogLevel.DEBUG }
    val visible = if (debug) loaded else loaded.filter { it.level != LogLevel.DEBUG }
    if (visible.isEmpty()) {
        LText("No log lines were recorded for this operation.", style = MaterialTheme.typography.bodySmall)
    } else {
        val shown = visible.takeLast(MAX_VISIBLE_LOG_LINES)
        if (shown.size < visible.size) {
            LText(
                "Showing the last ${shown.size} of ${visible.size} lines. Share the log to get everything.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEach { event -> LogLine(event) }
            }
        }
    }
    if (hiddenDetails > 0) {
        LText(
            "$hiddenDetails detail lines are hidden. Turn on Full debug logging to see them.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogLine(event: OperationLogEvent) {
    val color = when (event.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "${OperationLogStore.formatClock(event.timeMillis)} ${event.level.tag} ${event.message}",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
    )
}

private fun appInfoLine(context: Context): String {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"
    return "App ${context.packageName} $version · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · " +
        "${Build.MANUFACTURER} ${Build.MODEL}"
}

private fun copyReport(context: Context, report: String) {
    // Clipboard transfers are limited in size, so very long logs are trimmed to the newest part.
    val limit = 100_000
    val text = if (report.length > limit) report.take(limit) + "\n... trimmed, use Share for the full log ..." else report
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Shovel operation log", text))
    Toast.makeText(context, if (report.length > limit) "Copied the newest part of the log" else "Log copied", Toast.LENGTH_SHORT).show()
}

private fun shareReport(context: Context, report: String) {
    runCatching {
        val directory = File(context.cacheDir, "operation-log").apply { mkdirs() }
        val file = File(directory, "shovel-operation-log.txt")
        file.writeText(report)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Shovel operation log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "Could not share the log", Toast.LENGTH_SHORT).show()
    }
}
