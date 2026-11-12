package com.shovel.filemanager.advanced

import com.shovel.filemanager.core.FileSystemRules

object PrivilegedPathRules {
    const val MAX_DEPTH = 64
    const val MAX_SELECTED_ROOTS = 1_000
    const val MAX_TREE_ENTRIES = 100_000
    const val MAX_VISIBLE_ENTRIES = 20_000

    fun normalizeAbsolute(raw: String): String {
        require(raw.isNotBlank() && raw.startsWith('/')) { "An absolute Android path is required" }
        require('\u0000' !in raw && '\\' !in raw) { "Invalid Android path" }
        val segments = ArrayDeque<String>()
        raw.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> require(segments.isNotEmpty()) { "The path escapes its root" }.also { segments.removeLast() }
                else -> {
                    require(segment.length <= 255 && segment.none(Char::isISOControl)) { "Invalid path segment" }
                    segments.addLast(segment)
                }
            }
        }
        require(segments.size <= MAX_DEPTH) { "The Android path is too deep" }
        return "/" + segments.joinToString("/")
    }

    fun requireWithinAllowed(path: String, allowedRoots: List<String>, allowRoot: Boolean = true): String {
        val normalized = normalizeAbsolute(path)
        val root = allowedRoots.map(::normalizeAbsolute).firstOrNull { candidate ->
            candidate == "/" || normalized == candidate || normalized.startsWith("$candidate/")
        } ?: throw SecurityException("The path is outside the allowed privileged area")
        if (!allowRoot) require(normalized != root) { "The privileged root itself cannot be changed" }
        return normalized
    }

    fun child(parent: String, requestedName: String, allowedRoots: List<String>): String {
        val normalizedParent = requireWithinAllowed(parent, allowedRoots)
        val name = FileSystemRules.validateFileName(requestedName).getOrThrow()
        return requireWithinAllowed("$normalizedParent/$name", allowedRoots, allowRoot = false)
    }

    fun parent(path: String, allowedRoots: List<String>): String? {
        val normalized = requireWithinAllowed(path, allowedRoots)
        if (allowedRoots.any { normalizeAbsolute(it) == normalized }) return null
        return requireWithinAllowed(normalized.substringBeforeLast('/', "/"), allowedRoots)
    }
}
