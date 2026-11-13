package com.shovel.filemanager.data

import android.content.Context
import com.shovel.filemanager.model.SortDirection
import com.shovel.filemanager.model.SortMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

private const val CURRENT_WORKSPACE_SCHEMA = 4

data class WorkspaceTab(
    val id: String,
    val path: String,
    val locked: Boolean = false,
    val backHistory: List<String> = emptyList(),
    val forwardHistory: List<String> = emptyList(),
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val includeHidden: Boolean = false,
    val grid: Boolean = false,
    val returnToHomeAtBoundary: Boolean = false,
)

data class TabWorkspace(
    val tabs: List<WorkspaceTab>,
    val activeTabId: String,
    val closedTabs: List<WorkspaceTab> = emptyList(),
) {
    val activeTab: WorkspaceTab get() = tabs.first { it.id == activeTabId }
}

data class WorkspaceSession(
    val schemaVersion: Int = CURRENT_WORKSPACE_SCHEMA,
    val workspace: TabWorkspace,
)

class WorkspaceSessionRepository(context: Context) {
    companion object {
        const val MAX_TABS_PER_WORKSPACE = 16
        const val MAX_CLOSED_TABS = 8
        private const val MAX_HISTORY = 50
        private const val MAX_PATH_LENGTH = 4_096
        private const val MAX_FILE_BYTES = 1L * 1_024 * 1_024
        private val SAFE_ID = Regex("[A-Za-z0-9-]{1,80}")
    }

    private val file = File(context.filesDir, "workspace_session_v1.json")

    @Synchronized
    fun load(defaultPath: String): WorkspaceSession {
        if (!file.isFile) return defaultSession(defaultPath)
        require(file.length() in 1..MAX_FILE_BYTES) { "The workspace session file size is invalid" }
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val legacy = json.getInt("schemaVersion") < CURRENT_WORKSPACE_SCHEMA
            val session = parse(json)
            if (legacy) save(session)
            session
        }
            .getOrElse {
                val corrupt = File(file.parentFile, "workspace_session_v1.${System.currentTimeMillis()}.corrupt")
                file.renameTo(corrupt)
                defaultSession(defaultPath)
            }
    }

    @Synchronized
    fun save(session: WorkspaceSession) {
        validate(session)
        val bytes = session.toJson().toString().toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() in 1..MAX_FILE_BYTES) { "The workspace session is too large" }
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            runCatching {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            bytes.fill(0)
            if (temporary.exists()) temporary.delete()
        }
    }

    fun newTab(path: String): WorkspaceTab = WorkspaceTab(id = UUID.randomUUID().toString(), path = canonicalOrAbsolute(path))

    private fun defaultSession(path: String): WorkspaceSession {
        val tab = newTab(path)
        return WorkspaceSession(workspace = TabWorkspace(listOf(tab), tab.id))
    }

    private fun parse(json: JSONObject): WorkspaceSession {
        val sourceSchema = json.getInt("schemaVersion")
        require(sourceSchema in 1..CURRENT_WORKSPACE_SCHEMA) { "Unsupported workspace session version" }
        val resetLegacySort = sourceSchema == 1
        // Older sessions stored a left and a right side; only the left side (the main tab set) is kept.
        val workspaceJson = if (sourceSchema <= 3) json.getJSONObject("left") else json.getJSONObject("workspace")
        return WorkspaceSession(
            schemaVersion = CURRENT_WORKSPACE_SCHEMA,
            workspace = parseWorkspace(workspaceJson, resetLegacySort),
        ).also(::validate)
    }

    private fun parseWorkspace(json: JSONObject, resetLegacySort: Boolean): TabWorkspace {
        val tabs = json.getJSONArray("tabs").toTabs(resetLegacySort)
        val closed = json.optJSONArray("closedTabs")?.toTabs(resetLegacySort).orEmpty()
        return TabWorkspace(tabs, json.getString("activeTabId"), closed)
    }

    private fun JSONArray.toTabs(resetLegacySort: Boolean): List<WorkspaceTab> {
        require(length() <= MAX_TABS_PER_WORKSPACE) { "The tab limit was exceeded" }
        return (0 until length()).map { index ->
            val item = getJSONObject(index)
            WorkspaceTab(
                id = item.getString("id"),
                path = item.getString("path"),
                locked = item.optBoolean("locked", false),
                backHistory = item.optJSONArray("backHistory")?.toStrings(MAX_HISTORY).orEmpty(),
                forwardHistory = item.optJSONArray("forwardHistory")?.toStrings(MAX_HISTORY).orEmpty(),
                sortMode = if (resetLegacySort) SortMode.NAME else runCatching {
                    SortMode.valueOf(item.optString("sortMode", SortMode.NAME.name))
                }.getOrDefault(SortMode.NAME),
                sortDirection = if (resetLegacySort) SortDirection.ASCENDING else runCatching {
                    SortDirection.valueOf(item.optString("sortDirection", SortDirection.ASCENDING.name))
                }.getOrDefault(SortDirection.ASCENDING),
                includeHidden = item.optBoolean("includeHidden", false),
                grid = item.optBoolean("grid", false),
                returnToHomeAtBoundary = item.optBoolean("returnToHomeAtBoundary", false),
            )
        }
    }

    private fun JSONArray.toStrings(limit: Int): List<String> {
        require(length() <= limit) { "The navigation history limit was exceeded" }
        return (0 until length()).map(::getString)
    }

    private fun WorkspaceSession.toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("workspace", workspace.toJson())

    private fun TabWorkspace.toJson(): JSONObject = JSONObject()
        .put("tabs", JSONArray().apply { tabs.forEach { put(it.toJson()) } })
        .put("activeTabId", activeTabId)
        .put("closedTabs", JSONArray().apply { closedTabs.forEach { put(it.toJson()) } })

    private fun WorkspaceTab.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("path", path)
        .put("locked", locked)
        .put("backHistory", JSONArray().apply { backHistory.forEach(::put) })
        .put("forwardHistory", JSONArray().apply { forwardHistory.forEach(::put) })
        .put("sortMode", sortMode.name)
        .put("sortDirection", sortDirection.name)
        .put("includeHidden", includeHidden)
        .put("grid", grid)
        .put("returnToHomeAtBoundary", returnToHomeAtBoundary)

    private fun validate(session: WorkspaceSession) {
        require(session.schemaVersion == CURRENT_WORKSPACE_SCHEMA) { "Unsupported workspace session version" }
        validateWorkspace(session.workspace)
    }

    private fun validateWorkspace(workspace: TabWorkspace) {
        require(workspace.tabs.size in 1..MAX_TABS_PER_WORKSPACE) { "The tab count is invalid" }
        require(workspace.closedTabs.size <= MAX_CLOSED_TABS) { "The closed-tab limit was exceeded" }
        require(workspace.tabs.map(WorkspaceTab::id).distinct().size == workspace.tabs.size) { "Tab identities are duplicated" }
        require(workspace.activeTabId in workspace.tabs.map(WorkspaceTab::id)) { "The active tab was not found" }
        (workspace.tabs + workspace.closedTabs).forEach(::validateTab)
    }

    private fun validateTab(tab: WorkspaceTab) {
        require(SAFE_ID.matches(tab.id)) { "Invalid tab identity" }
        validatePath(tab.path)
        require(tab.backHistory.size <= MAX_HISTORY && tab.forwardHistory.size <= MAX_HISTORY) { "The navigation history limit was exceeded" }
        tab.backHistory.forEach(::validatePath)
        tab.forwardHistory.forEach(::validatePath)
    }

    private fun validatePath(path: String) = require(path.isNotBlank() && path.length <= MAX_PATH_LENGTH && '\u0000' !in path) { "Invalid tab path" }
    private fun canonicalOrAbsolute(path: String): String = runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }
}
