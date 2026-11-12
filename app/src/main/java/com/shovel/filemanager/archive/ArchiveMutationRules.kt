package com.shovel.filemanager.archive

import java.util.Locale

internal data class ArchiveRenamePlan(
    val sourcePath: String,
    val targetPath: String,
    val renamedHeaders: Map<String, String>,
)

/** Pure, bounded planning rules used before any archive is rewritten. */
internal object ArchiveMutationRules {
    private const val MAX_SELECTED_PATHS = 10_000
    private const val MAX_NAME_CHARACTERS = 255

    fun extractionBaseName(archiveName: String, fallback: String): String {
        val name = archiveName.trim()
        val lower = name.lowercase(Locale.ROOT)
        val withoutSuffix = when {
            lower.endsWith(".tar.gz") -> name.dropLast(7)
            lower.endsWith(".tgz") -> name.dropLast(4)
            '.' in name -> name.substringBeforeLast('.')
            else -> name
        }
        return withoutSuffix.ifBlank { fallback.ifBlank { "extracted" } }
    }

    fun deletionHeaders(
        entries: List<ArchiveEntryInfo>,
        selectedPaths: Collection<String>,
    ): List<String> {
        val selected = normalizeSelection(selectedPaths)
        val affected = entries.asSequence()
            .map(ArchiveEntryInfo::name)
            .filter { original ->
                val normalized = normalizePath(original)
                selected.any { selectedPath -> isAtOrBelow(normalized, selectedPath) }
            }
            .distinct()
            .toList()
        require(affected.isNotEmpty()) { "The selected archive entries no longer exist" }
        return affected
    }

    fun renamePlan(
        entries: List<ArchiveEntryInfo>,
        sourcePath: String,
        requestedName: String,
    ): ArchiveRenamePlan {
        val source = normalizePath(sourcePath)
        val newName = validateLeafName(requestedName)
        val parent = source.substringBeforeLast('/', missingDelimiterValue = "")
        val target = if (parent.isEmpty()) newName else "$parent/$newName"
        require(target != source) { "The name has not changed" }

        val normalizedEntries = entries.map { entry -> entry.name to normalizePath(entry.name) }
        val affected = normalizedEntries.filter { (_, normalized) -> isAtOrBelow(normalized, source) }
        require(affected.isNotEmpty()) { "The archive entry no longer exists" }

        val sourceIsDirectory = affected.any { (_, normalized) -> normalized != source } ||
            entries.any { it.directory && normalizePath(it.name) == source }
        if (!sourceIsDirectory) {
            require(affected.size == 1 && affected.single().second == source) { "The archive entry type is unknown" }
        }

        val affectedNormalized = affected.mapTo(hashSetOf()) { it.second }
        val renamed = linkedMapOf<String, String>()
        affected.forEach { (original, normalized) ->
            val suffix = normalized.removePrefix(source)
            val renamedPath = target + suffix
            renamed[original] = if (original.replace('\\', '/').endsWith('/')) "$renamedPath/" else renamedPath
        }
        val targetNormalized = renamed.values.map(::normalizePath)
        require(targetNormalized.distinct().size == targetNormalized.size) { "The new archive paths repeat" }
        val unaffected = normalizedEntries.map { it.second }.filterNot(affectedNormalized::contains).toSet()
        require(targetNormalized.none(unaffected::contains)) { "An archive entry with this name already exists" }
        return ArchiveRenamePlan(source, target, renamed)
    }

    fun normalizeSelection(paths: Collection<String>): List<String> {
        require(paths.isNotEmpty()) { "No archive entries selected" }
        require(paths.size <= MAX_SELECTED_PATHS) { "Too many archive entries selected" }
        val ordered = paths.map(::normalizePath).distinct().sortedBy(String::length)
        return ordered.filter { candidate ->
            ordered.none { other -> other != candidate && other.length < candidate.length && isAtOrBelow(candidate, other) }
        }
    }

    fun normalizePath(raw: String): String {
        val normalized = raw.replace('\\', '/').trim('/')
        require(normalized.isNotBlank() && '\u0000' !in normalized) { "Invalid archive path" }
        val segments = normalized.split('/')
        require(segments.size <= 64 && segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Unsafe archive path"
        }
        return segments.joinToString("/")
    }

    private fun validateLeafName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME_CHARACTERS) { "Invalid archive entry name length" }
        require(name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' || it.isISOControl() }) {
            "The archive entry name contains invalid characters"
        }
        return name
    }

    private fun isAtOrBelow(path: String, root: String): Boolean = path == root || path.startsWith("$root/")
}
