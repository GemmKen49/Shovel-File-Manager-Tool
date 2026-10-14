package com.shovel.filemanager.operations

import com.shovel.filemanager.core.FileSystemRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class OperationStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    SUCCEEDED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

data class OperationSnapshot(
    val id: String,
    val title: String,
    val status: OperationStatus,
    val completedItems: Int = 0,
    val totalItems: Int? = null,
    val completedBytes: Long = 0,
    val totalBytes: Long? = null,
    val currentName: String? = null,
    val message: String? = null,
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val retryable: Boolean = false,
    val errorCount: Int = 0,
)

class OperationContext internal constructor(
    private val id: String,
    private val paused: MutableStateFlow<Boolean>,
    private val logger: (LogLevel, String) -> Unit,
    private val publish: (OperationSnapshot.() -> OperationSnapshot) -> Unit,
) {
    internal var completionStatus: OperationStatus = OperationStatus.SUCCEEDED
        private set
    private var pendingItems = 0
    private var pendingBytes = 0L
    private var pendingName: String? = null
    private var lastProgressPublishNanos = 0L
    private var lastLoggedName: String? = null

    companion object {
        private const val PROGRESS_PUBLISH_INTERVAL_NANOS = 100_000_000L
        fun background(): OperationContext = OperationContext("background", MutableStateFlow(false), { _, _ -> }) { }
    }

    suspend fun checkpoint() {
        currentCoroutineContext().ensureActive()
        if (paused.value) {
            flushProgress()
            publish { copy(status = OperationStatus.PAUSED) }
            logger(LogLevel.INFO, "Paused")
            paused.first { value -> !value }
            publish { copy(status = OperationStatus.RUNNING) }
            logger(LogLevel.INFO, "Resumed")
        }
        currentCoroutineContext().ensureActive()
    }

    fun setTotals(items: Int?, bytes: Long?) {
        publish { copy(totalItems = items, totalBytes = bytes) }
        logger(
            LogLevel.DEBUG,
            "Totals: ${items?.toString() ?: "unknown"} items, ${bytes?.let(FileSystemRules::humanBytes) ?: "unknown size"}",
        )
    }

    @Synchronized
    fun progress(itemDelta: Int = 0, byteDelta: Long = 0, currentName: String? = null) {
        pendingItems = Math.addExact(pendingItems, itemDelta)
        pendingBytes = Math.addExact(pendingBytes, byteDelta)
        if (currentName != null) pendingName = currentName
        if (currentName != null && currentName != lastLoggedName) {
            lastLoggedName = currentName
            logger(LogLevel.DEBUG, "Processing: $currentName")
        }
        if (itemDelta > 1) logger(LogLevel.DEBUG, "Completed $itemDelta items in one step")
        val now = System.nanoTime()
        if (itemDelta != 0 || lastProgressPublishNanos == 0L ||
            now - lastProgressPublishNanos >= PROGRESS_PUBLISH_INTERVAL_NANOS
        ) {
            flushProgressLocked(now)
        }
    }

    @Synchronized
    internal fun flushProgress() {
        flushProgressLocked(System.nanoTime())
    }

    private fun flushProgressLocked(now: Long) {
        if (pendingItems == 0 && pendingBytes == 0L && pendingName == null) return
        val itemDelta = pendingItems
        val byteDelta = pendingBytes
        val currentName = pendingName
        pendingItems = 0
        pendingBytes = 0L
        pendingName = null
        lastProgressPublishNanos = now
        publish {
            copy(
                completedItems = completedItems + itemDelta,
                completedBytes = completedBytes + byteDelta,
                currentName = currentName ?: this.currentName,
            )
        }
    }

    fun note(message: String) {
        flushProgress()
        publish { copy(message = message) }
        logger(LogLevel.INFO, message)
    }

    // Named "log…" so they never shadow Kotlin's error(), which operation blocks use to throw.
    fun logDebug(message: String) = logger(LogLevel.DEBUG, message)

    fun logInfo(message: String) = logger(LogLevel.INFO, message)

    fun logWarn(message: String) = logger(LogLevel.WARN, message)

    fun logError(message: String) = logger(LogLevel.ERROR, message)

    fun completeWithErrors(errorCount: Int, message: String) {
        require(errorCount > 0) { "Klaidų skaičius turi būti teigiamas" }
        flushProgress()
        completionStatus = OperationStatus.COMPLETED_WITH_ERRORS
        publish { copy(errorCount = errorCount, message = message, retryable = true) }
        logger(LogLevel.WARN, "$message ($errorCount errors)")
    }

    fun setRetryable(value: Boolean) {
        publish { copy(retryable = value) }
    }
}

class FileOperationManager(
    private val scope: CoroutineScope,
    private val logs: OperationLogStore? = null,
    private val onOperationQueued: () -> Unit = {},
) {
    companion object {
        private const val MAX_QUEUED_OPERATIONS = 32
        private const val MAX_VISIBLE_HISTORY = 64
        private const val MAX_STACK_LINES = 25
    }

    private data class Request(
        val id: String,
        val title: String,
        val block: suspend OperationContext.() -> Unit,
    )

    private val queue = Channel<Request>(capacity = MAX_QUEUED_OPERATIONS)
    private val pauseSignals = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val cancelledBeforeStart = ConcurrentHashMap.newKeySet<String>()
    private val _operations = MutableStateFlow<List<OperationSnapshot>>(emptyList())
    val operations: StateFlow<List<OperationSnapshot>> = _operations.asStateFlow()

    init {
        scope.launch {
            for (request in queue) {
                if (cancelledBeforeStart.remove(request.id)) continue
                val paused = pauseSignals.getOrPut(request.id) { MutableStateFlow(false) }
                update(request.id) {
                    copy(status = OperationStatus.RUNNING, startedAtMillis = System.currentTimeMillis())
                }
                logs?.started(request.id)
                val operationJob = launch(start = CoroutineStart.LAZY) {
                    try {
                        val context = OperationContext(
                            request.id,
                            paused,
                            { level, text -> logs?.record(request.id, level, text) },
                        ) { transform -> update(request.id, transform) }
                        request.block(context)
                        context.flushProgress()
                        update(request.id) {
                            copy(
                                status = context.completionStatus,
                                currentName = null,
                                finishedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        finishLog(request.id)
                    } catch (_: CancellationException) {
                        logs?.record(request.id, LogLevel.INFO, "Cancelled")
                        update(request.id) {
                            copy(status = OperationStatus.CANCELLED, finishedAtMillis = System.currentTimeMillis())
                        }
                        finishLog(request.id)
                    } catch (error: Throwable) {
                        logs?.record(request.id, LogLevel.ERROR, "Failed: ${error.message ?: error::class.java.simpleName}")
                        logs?.record(request.id, LogLevel.DEBUG, "Stack trace:")
                        error.stackTraceToString().lineSequence().take(MAX_STACK_LINES).forEach { line ->
                            logs?.record(request.id, LogLevel.DEBUG, "    $line")
                        }
                        update(request.id) {
                            copy(
                                status = OperationStatus.FAILED,
                                message = error.message ?: error::class.java.simpleName,
                                finishedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        finishLog(request.id)
                    } finally {
                        runningJobs.remove(request.id)
                        pauseSignals.remove(request.id)
                    }
                }
                runningJobs[request.id] = operationJob
                operationJob.start()
                operationJob.join()
            }
        }
    }

    fun submit(title: String, block: suspend OperationContext.() -> Unit): Result<String> {
        val id = UUID.randomUUID().toString()
        return submitExisting(id, title, retryable = false, block = block)
    }

    fun submitExisting(
        id: String,
        title: String,
        retryable: Boolean,
        block: suspend OperationContext.() -> Unit,
    ): Result<String> {
        require(id.isNotBlank() && id.length <= 80) { "Netinkama operacijos tapatybė" }
        if (runningJobs.containsKey(id)) return Result.failure(IllegalStateException("Operacija jau vykdoma"))
        val snapshot = OperationSnapshot(id = id, title = title, status = OperationStatus.QUEUED, retryable = retryable)
        _operations.update { current ->
            (listOf(snapshot) + current.filterNot { it.id == id }).take(MAX_VISIBLE_HISTORY)
        }
        logs?.begin(id, title)
        val result = queue.trySend(Request(id, title, block))
        if (result.isFailure) {
            logs?.record(id, LogLevel.ERROR, "The operation queue is full")
            update(id) { copy(status = OperationStatus.FAILED, message = "Operacijų eilė pilna") }
            finishLog(id)
            return Result.failure(IllegalStateException("Operacijų eilė pilna"))
        }
        runCatching(onOperationQueued)
        return Result.success(id)
    }

    fun restore(snapshots: List<OperationSnapshot>) {
        require(snapshots.size <= MAX_VISIBLE_HISTORY) { "Operacijų istorijos riba viršyta" }
        _operations.update { current ->
            (snapshots + current.filterNot { existing -> snapshots.any { it.id == existing.id } })
                .distinctBy(OperationSnapshot::id)
                .take(MAX_VISIBLE_HISTORY)
        }
    }

    fun pause(id: String) {
        pauseSignals[id]?.value = true
    }

    fun resume(id: String) {
        pauseSignals[id]?.value = false
    }

    fun cancel(id: String) {
        val job = runningJobs[id]
        if (job != null) {
            job.cancel()
        } else {
            cancelledBeforeStart.add(id)
            logs?.record(id, LogLevel.INFO, "Cancelled before it started")
            update(id) { copy(status = OperationStatus.CANCELLED, finishedAtMillis = System.currentTimeMillis()) }
            finishLog(id)
        }
    }

    fun dismissFinished() {
        _operations.update { snapshots ->
            snapshots.filter { it.status == OperationStatus.RUNNING || it.status == OperationStatus.PAUSED || it.status == OperationStatus.QUEUED }
        }
    }

    private fun finishLog(id: String) {
        val store = logs ?: return
        _operations.value.firstOrNull { it.id == id }?.let(store::finish)
    }

    private fun update(id: String, transform: OperationSnapshot.() -> OperationSnapshot) {
        _operations.update { snapshots -> snapshots.map { if (it.id == id) it.transform() else it } }
    }
}
