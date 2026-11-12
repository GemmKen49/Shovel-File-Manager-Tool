package com.shovel.filemanager.advanced

import com.shovel.filemanager.editing.EditLimits
import com.shovel.filemanager.model.FileEntry
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class PrivilegedPreviewCache(cacheDirectory: File) {
    private val root = File(cacheDirectory, "privileged-previews")

    init {
        clearStale()
    }

    @Synchronized fun createDestination(entry: FileEntry): File {
        require(!entry.isDirectory && entry.sizeBytes in 0..EditLimits.MAX_FILE_BYTES) { "Invalid file for preview" }
        require(root.mkdirs() || root.isDirectory) { "Could not create the preview cache" }
        // A second request must not delete media still owned by the first player.
        // The caller discards its own destination on close/failure; startup clears stale copies.
        require(root.listFiles()?.size?.let { it < 2 } == true) { "Close the previous preview first" }
        val required = Math.addExact(entry.sizeBytes, EditLimits.MIN_FREE_BYTES)
        require(root.usableSpace <= 0L || root.usableSpace >= required) { "There is not enough free space for the preview" }
        val identity = "${entry.absolutePath}\u0000${entry.sizeBytes}\u0000${entry.modifiedAtMillis}\u0000${UUID.randomUUID()}"
        val directory = File(root, sha256(identity))
        require(directory.mkdir()) { "Could not create the preview folder" }
        val extension = entry.extension.takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }
        val destination = File(directory, extension?.let { "content.$it" } ?: "content.bin")
        require(destination.canonicalFile.parentFile == directory.canonicalFile) { "The preview path escapes the cache" }
        return destination
    }

    fun validateCompleted(destination: File) {
        require(destination.isFile && destination.length() <= EditLimits.MAX_FILE_BYTES) { "The preview copy is incomplete" }
    }

    @Synchronized fun discard(destination: File?): Boolean {
        val directory = destination?.parentFile ?: return true
        if (!directory.exists()) return true
        val contained = runCatching { directory.canonicalFile.parentFile == root.canonicalFile }.getOrDefault(false)
        return contained && directory.deleteRecursively()
    }

    private fun clearStale(): Boolean = !root.exists() || root.deleteRecursively()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
