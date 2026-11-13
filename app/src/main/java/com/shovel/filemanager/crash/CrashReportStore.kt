package com.shovel.filemanager.crash

import android.content.Context
import android.os.Build
import com.shovel.filemanager.operations.OperationLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** The saved stack trace of the last crash and when it happened. */
data class CrashReport(
    val timeMillis: Long,
    val text: String,
)

/**
 * Saves the stack trace of an uncaught exception to a file so it can be shown after the app restarts.
 *
 * The report stays on this device until the user dismisses it. Only the newest crash is kept.
 * Call [install] once, as early as possible in `Application.onCreate`.
 */
class CrashReportStore(context: Context) {
    private val file = reportFile(context)

    private val _report = MutableStateFlow<CrashReport?>(null)

    /** The saved crash report, or null when there is none. */
    val report: StateFlow<CrashReport?> = _report.asStateFlow()

    init {
        // Reading the file must not delay app start.
        Thread({ _report.value = read() }, "crash-report-load").apply { isDaemon = true }.start()
    }

    /** Removes the saved report. */
    fun dismiss() {
        _report.value = null
        runCatching { file.delete() }
    }

    private fun read(): CrashReport? = runCatching {
        if (file.isFile) CrashReport(file.lastModified(), file.readText()) else null
    }.getOrNull()

    companion object {
        private const val FILE_NAME = "crash_report.txt"
        private const val MAX_CHARS = 100_000

        private fun reportFile(context: Context) = File(context.filesDir, FILE_NAME)

        /** Records every uncaught exception, then hands it to the previous handler so the app closes as usual. */
        fun install(context: Context) {
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { write(appContext, thread, throwable) }
                previous?.uncaughtException(thread, throwable)
            }
        }

        private fun write(context: Context, thread: Thread, throwable: Throwable) {
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val text = buildString {
                append("Shovel crash report\n")
                append(appInfo(context)).append('\n')
                append("Time: ").append(OperationLogStore.formatTimestamp(System.currentTimeMillis())).append('\n')
                append("Thread: ").append(thread.name).append("\n\n")
                append(trace)
            }.take(MAX_CHARS)
            val target = reportFile(context)
            val temp = File(target.parentFile, "$FILE_NAME.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        }

        private fun appInfo(context: Context): String {
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "unknown"
            return "App ${context.packageName} $version · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }
}
