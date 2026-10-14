package com.shovel.filemanager.operations

import android.content.Context
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.ui.localization.UiTranslator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val tag: String) {
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
    ;

    companion object {
        fun fromTag(tag: String): LogLevel = entries.firstOrNull { it.tag == tag } ?: INFO
    }
}

data class OperationLogEvent(
    val timeMillis: Long,
    val level: LogLevel,
    val message: String,
)

/** Everything the Operations page needs to list one operation without reading its events. */
data class OperationLogSummary(
    val id: String,
    val title: String,
    val status: OperationStatus,
    val queuedAtMillis: Long,
    val startedAtMillis: Long?,
    val finishedAtMillis: Long?,
    val completedItems: Int,
    val totalItems: Int?,
    val completedBytes: Long,
    val totalBytes: Long?,
    val errorCount: Int,
    val message: String?,
    val droppedEvents: Int,
)

/**
 * Keeps a readable history of what each file operation did, on this device only, until the user clears it.
 *
 * Simple mode records the story of an operation (started, notes, warnings, errors, result).
 * Full debug mode also records every file that was processed and technical error details.
 * Layout on disk: `<id>.json` (summary) and `<id>.log` (one JSON event per line).
 */
class OperationLogStore(context: Context) {
    private val directory = File(context.filesDir, "operation_logs").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("operation_logs", Context.MODE_PRIVATE)
    private val lock = Any()

    private class Live(
        var summary: OperationLogSummary,
        val events: ArrayList<OperationLogEvent> = ArrayList(),
        var flushedCount: Int = 0,
        var counted: Int = 0,
    )

    private val live = HashMap<String, Live>()

    private val _debugMode = MutableStateFlow(preferences.getBoolean(KEY_DEBUG, false))
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    private val _summaries = MutableStateFlow<List<OperationLogSummary>>(emptyList())

    /** Newest first. */
    val summaries: StateFlow<List<OperationLogSummary>> = _summaries.asStateFlow()

    init {
        // Reading many small files must not delay app start.
        Thread({
            val loaded = loadSummaries()
            synchronized(lock) {
                _summaries.update { current ->
                    (current + loaded.filter { saved -> current.none { it.id == saved.id } })
                        .sortedByDescending { it.queuedAtMillis }
                }
            }
        }, "operation-log-load").apply { isDaemon = true }.start()
    }

    fun setDebugMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DEBUG, enabled).apply()
        _debugMode.value = enabled
    }

    fun begin(id: String, title: String) {
        val now = System.currentTimeMillis()
        val englishTitle = UiTranslator.translate(title)
        synchronized(lock) {
            val existing = live[id]?.summary ?: _summaries.value.firstOrNull { it.id == id }
            val summary = OperationLogSummary(
                id = id,
                title = englishTitle,
                status = OperationStatus.QUEUED,
                queuedAtMillis = existing?.queuedAtMillis ?: now,
                startedAtMillis = null,
                finishedAtMillis = null,
                completedItems = 0,
                totalItems = null,
                completedBytes = 0,
                totalBytes = null,
                errorCount = 0,
                message = null,
                droppedEvents = existing?.droppedEvents ?: 0,
            )
            val entry = live.getOrPut(id) {
                Live(summary).also { fresh ->
                    if (existing != null) {
                        val old = readEvents(id)
                        fresh.events.addAll(old)
                        fresh.flushedCount = old.size
                    }
                }
            }
            entry.summary = summary
            if (existing != null) {
                appendLocked(id, entry, OperationLogEvent(now, LogLevel.INFO, "Retry requested"))
            }
            appendLocked(id, entry, OperationLogEvent(now, LogLevel.INFO, "Queued: $englishTitle"))
            publishLocked(entry.summary)
        }
    }

    fun started(id: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = live[id] ?: return
            entry.summary = entry.summary.copy(status = OperationStatus.RUNNING, startedAtMillis = now)
            appendLocked(id, entry, OperationLogEvent(now, LogLevel.INFO, "Started"))
            publishLocked(entry.summary)
        }
    }

    fun record(id: String, level: LogLevel, message: String) {
        if (level == LogLevel.DEBUG && !_debugMode.value) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = live[id] ?: return
            // Detail lines are already English; only story lines can come from Lithuanian source text.
            val text = (if (level == LogLevel.DEBUG) message else UiTranslator.translate(message)).take(MAX_LINE_LENGTH)
            appendLocked(id, entry, OperationLogEvent(now, level, text))
        }
    }

    /** Writes the final result and moves the operation from "live" to saved history. */
    fun finish(snapshot: OperationSnapshot) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val entry = live[snapshot.id] ?: return
            val started = snapshot.startedAtMillis ?: entry.summary.startedAtMillis
            val finished = snapshot.finishedAtMillis ?: now
            entry.summary = entry.summary.copy(
                status = snapshot.status,
                startedAtMillis = started,
                finishedAtMillis = finished,
                completedItems = snapshot.completedItems,
                totalItems = snapshot.totalItems,
                completedBytes = snapshot.completedBytes,
                totalBytes = snapshot.totalBytes,
                errorCount = snapshot.errorCount,
                message = snapshot.message?.let { UiTranslator.translate(it) },
            )
            val level = when (snapshot.status) {
                OperationStatus.FAILED -> LogLevel.ERROR
                OperationStatus.COMPLETED_WITH_ERRORS, OperationStatus.INTERRUPTED -> LogLevel.WARN
                else -> LogLevel.INFO
            }
            val duration = if (started != null) " in ${formatDuration(finished - started)}" else ""
            val counts = buildString {
                append(snapshot.completedItems)
                snapshot.totalItems?.let { append("/").append(it) }
                append(" items")
                if (snapshot.completedBytes > 0) append(", ").append(FileSystemRules.humanBytes(snapshot.completedBytes))
            }
            appendLocked(
                snapshot.id,
                entry,
                OperationLogEvent(now, level, "Finished: ${statusLabel(snapshot.status)}$duration ($counts)"),
            )
            flushLocked(snapshot.id, entry)
            writeSummaryLocked(entry.summary)
            live.remove(snapshot.id)
            publishLocked(entry.summary)
            trimLocked()
        }
    }

    /** Events for one operation, oldest first. Reads from disk for finished operations, so call off the main thread. */
    fun events(id: String): List<OperationLogEvent> {
        synchronized(lock) {
            live[id]?.let { return ArrayList(it.events) }
        }
        return readEvents(id)
    }

    private fun readEvents(id: String): List<OperationLogEvent> {
        val file = File(directory, "$id.log")
        if (!file.isFile) return emptyList()
        val result = ArrayList<OperationLogEvent>()
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    runCatching {
                        val json = JSONObject(line)
                        result += OperationLogEvent(
                            timeMillis = json.getLong("t"),
                            level = LogLevel.fromTag(json.optString("l", "I")),
                            message = json.optString("m"),
                        )
                    }
                }
            }
        }
        return result
    }

    /** Removes saved history. Operations that are still queued or running are kept. */
    fun clear() {
        synchronized(lock) {
            directory.listFiles()?.forEach { file ->
                val id = file.name.substringBeforeLast('.')
                if (id !in live) file.delete()
            }
            _summaries.value = live.values.map { it.summary }.sortedByDescending { it.queuedAtMillis }
        }
    }

    /** Plain-text report of all operations, newest first. Capped so it can be shared. */
    fun exportText(appInfo: String): String {
        val builder = StringBuilder()
        builder.append("Shovel operation log\n")
        builder.append(appInfo).append('\n')
        builder.append("Mode: ").append(if (_debugMode.value) "full debug" else "simple").append('\n')
        builder.append("Exported: ").append(formatTimestamp(System.currentTimeMillis())).append("\n\n")
        val showDebug = _debugMode.value
        for (summary in _summaries.value) {
            if (builder.length > MAX_EXPORT_CHARS) {
                builder.append("... older operations omitted ...\n")
                break
            }
            builder.append("== ").append(summary.title).append(" [").append(statusLabel(summary.status)).append("]\n")
            builder.append("   queued ").append(formatTimestamp(summary.queuedAtMillis))
            summary.finishedAtMillis?.let { builder.append(", finished ").append(formatTimestamp(it)) }
            builder.append("\n   id ").append(summary.id).append('\n')
            for (event in events(summary.id)) {
                if (event.level == LogLevel.DEBUG && !showDebug) continue
                builder.append("   ")
                    .append(formatClock(event.timeMillis)).append(' ')
                    .append(event.level.tag).append(' ')
                    .append(event.message).append('\n')
            }
            if (summary.droppedEvents > 0) {
                builder.append("   (").append(summary.droppedEvents).append(" detail lines were not recorded)\n")
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    fun totalSizeBytes(): Long = directory.listFiles()?.sumOf(File::length) ?: 0L

    // ---------------------------------------------------------------------------------------------

    private fun appendLocked(id: String, entry: Live, event: OperationLogEvent) {
        val cap = if (_debugMode.value) MAX_EVENTS_DEBUG else MAX_EVENTS_SIMPLE
        val size = entry.events.size
        val allowed = when (event.level) {
            LogLevel.DEBUG -> size < cap
            LogLevel.INFO -> size < cap + RESERVED_EVENTS
            LogLevel.WARN, LogLevel.ERROR -> size < cap + 2 * RESERVED_EVENTS
        }
        if (!allowed) {
            if (event.level != LogLevel.INFO) {
                entry.summary = entry.summary.copy(droppedEvents = entry.summary.droppedEvents + 1)
            }
            return
        }
        entry.events += event
        val important = event.level == LogLevel.WARN || event.level == LogLevel.ERROR
        if (entry.events.size - entry.flushedCount >= FLUSH_EVERY || important) flushLocked(id, entry)
    }

    private fun flushLocked(id: String, entry: Live) {
        if (entry.flushedCount >= entry.events.size) return
        runCatching {
            java.io.FileOutputStream(File(directory, "$id.log"), true).bufferedWriter().use { writer ->
                for (index in entry.flushedCount until entry.events.size) {
                    val event = entry.events[index]
                    writer.write(
                        JSONObject()
                            .put("t", event.timeMillis)
                            .put("l", event.level.tag)
                            .put("m", event.message)
                            .toString(),
                    )
                    writer.write("\n")
                }
            }
        }
        entry.flushedCount = entry.events.size
        writeSummaryLocked(entry.summary)
    }

    private fun writeSummaryLocked(summary: OperationLogSummary) {
        runCatching {
            val json = JSONObject()
                .put("id", summary.id)
                .put("title", summary.title)
                .put("status", summary.status.name)
                .put("queued", summary.queuedAtMillis)
                .put("started", summary.startedAtMillis ?: JSONObject.NULL)
                .put("finished", summary.finishedAtMillis ?: JSONObject.NULL)
                .put("items", summary.completedItems)
                .put("totalItems", summary.totalItems ?: JSONObject.NULL)
                .put("bytes", summary.completedBytes)
                .put("totalBytes", summary.totalBytes ?: JSONObject.NULL)
                .put("errors", summary.errorCount)
                .put("message", summary.message ?: JSONObject.NULL)
                .put("dropped", summary.droppedEvents)
            val target = File(directory, "${summary.id}.json")
            val temp = File(directory, "${summary.id}.json.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.writeText(json.toString())
                temp.delete()
            }
        }
    }

    private fun publishLocked(summary: OperationLogSummary) {
        _summaries.update { current ->
            (listOf(summary) + current.filterNot { it.id == summary.id }).sortedByDescending { it.queuedAtMillis }
        }
    }

    private fun trimLocked() {
        val files = directory.listFiles().orEmpty()
        val ids = files.map { it.name.substringBefore('.') }.toSet()
        var total = files.sumOf(File::length)
        val ordered = _summaries.value.filter { it.id !in live }.sortedBy { it.queuedAtMillis } // oldest first
        var count = ids.size
        for (summary in ordered) {
            if (count <= MAX_OPERATIONS && total <= MAX_TOTAL_BYTES) break
            val log = File(directory, "${summary.id}.log")
            val meta = File(directory, "${summary.id}.json")
            total -= log.length() + meta.length()
            log.delete()
            meta.delete()
            count -= 1
            _summaries.update { list -> list.filterNot { it.id == summary.id } }
        }
    }

    private fun loadSummaries(): List<OperationLogSummary> {
        val files = directory.listFiles { file -> file.name.endsWith(".json") }.orEmpty()
        val loaded = files.mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                var status = OperationStatus.valueOf(json.getString("status"))
                var finished = if (json.isNull("finished")) null else json.getLong("finished")
                if (status == OperationStatus.QUEUED || status == OperationStatus.RUNNING || status == OperationStatus.PAUSED) {
                    // The app was closed while this operation was in flight.
                    status = OperationStatus.INTERRUPTED
                    finished = finished ?: file.lastModified()
                }
                OperationLogSummary(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    status = status,
                    queuedAtMillis = json.getLong("queued"),
                    startedAtMillis = if (json.isNull("started")) null else json.getLong("started"),
                    finishedAtMillis = finished,
                    completedItems = json.optInt("items"),
                    totalItems = if (json.isNull("totalItems")) null else json.getInt("totalItems"),
                    completedBytes = json.optLong("bytes"),
                    totalBytes = if (json.isNull("totalBytes")) null else json.getLong("totalBytes"),
                    errorCount = json.optInt("errors"),
                    message = if (json.isNull("message")) null else json.optString("message"),
                    droppedEvents = json.optInt("dropped"),
                )
            }.getOrNull()
        }
        return loaded.sortedByDescending { it.queuedAtMillis }
    }

    companion object {
        private const val KEY_DEBUG = "debug_mode"
        private const val MAX_EVENTS_SIMPLE = 300
        private const val MAX_EVENTS_DEBUG = 5_000
        private const val RESERVED_EVENTS = 100
        private const val FLUSH_EVERY = 100
        private const val MAX_LINE_LENGTH = 400
        private const val MAX_OPERATIONS = 500
        private const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
        private const val MAX_EXPORT_CHARS = 400_000

        fun statusLabel(status: OperationStatus): String = when (status) {
            OperationStatus.QUEUED -> "queued"
            OperationStatus.RUNNING -> "running"
            OperationStatus.PAUSED -> "paused"
            OperationStatus.SUCCEEDED -> "succeeded"
            OperationStatus.COMPLETED_WITH_ERRORS -> "finished with errors"
            OperationStatus.FAILED -> "failed"
            OperationStatus.CANCELLED -> "cancelled"
            OperationStatus.INTERRUPTED -> "interrupted"
        }

        fun formatDuration(millis: Long): String {
            val total = (millis.coerceAtLeast(0L) / 1_000)
            val hours = total / 3_600
            val minutes = (total % 3_600) / 60
            val seconds = total % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m ${seconds}s"
                millis < 1_000 -> "${millis.coerceAtLeast(0L)} ms"
                else -> "${seconds}s"
            }
        }

        fun formatClock(millis: Long): String =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))

        fun formatTimestamp(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }
}
