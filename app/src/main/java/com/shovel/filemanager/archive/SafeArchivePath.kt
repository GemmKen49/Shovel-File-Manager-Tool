package com.shovel.filemanager.archive

import com.shovel.filemanager.core.FileSystemRules
import java.io.File

object SafeArchivePath {
    fun resolve(destinationRoot: File, entryName: String, maxDepth: Int = 64): File {
        require('\u0000' !in entryName) { "The archive entry contains a NUL character" }
        val normalized = entryName.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank()) { "The archive entry name is empty" }
        require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "Absolute Windows paths are not allowed in archives" }
        val components = normalized.split('/').filter(String::isNotBlank)
        require(components.size <= maxDepth) { "The archive folder depth exceeds the limit" }
        require(components.none { it == "." || it == ".." }) { "Archive path traversal is not allowed" }
        val target = components.fold(destinationRoot) { parent, component -> File(parent, component) }
        require(FileSystemRules.isContained(destinationRoot, target)) { "The archive entry escapes the destination folder" }
        return target
    }
}
