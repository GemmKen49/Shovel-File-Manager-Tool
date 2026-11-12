package com.shovel.filemanager.pdfsigning

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class SavedSignature(
    val id: String,
    val name: String,
    val drawing: SignatureDrawing,
    val createdAtMillis: Long,
)

/** Persists only bounded vector drawings inside Shovel's private files directory. */
class SignatureLibraryRepository(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_SIGNATURES = 20
        const val MAX_NAME_LENGTH = 60
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_BYTES = 4L * 1_024 * 1_024
        private val SAFE_ID = Regex("[0-9a-fA-F-]{36}")
    }

    private val storageFile = File(directory, "signature_library_v1.json")

    @Synchronized
    fun load(): List<SavedSignature> {
        if (!storageFile.exists()) return emptyList()
        require(storageFile.isFile && storageFile.length() in 1..MAX_FILE_BYTES) {
            "The signature library file size is invalid"
        }
        val root = JSONObject(storageFile.readText(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "The signature library version is unsupported" }
        val records = root.getJSONArray("signatures")
        require(records.length() <= MAX_SIGNATURES) { "The signature library limit was exceeded" }
        val parsed = List(records.length()) { index -> parseSignature(records.getJSONObject(index)) }
        require(parsed.map(SavedSignature::id).distinct().size == parsed.size) { "The signature library contains duplicate identifiers" }
        return parsed.sortedByDescending(SavedSignature::createdAtMillis)
    }

    @Synchronized
    fun save(name: String, drawing: SignatureDrawing): List<SavedSignature> {
        val current = load()
        require(current.size < MAX_SIGNATURES) { "The signature library limit was exceeded" }
        val record = SavedSignature(
            id = UUID.randomUUID().toString(),
            name = normalizeName(name),
            drawing = VisualSignatureRules.validate(drawing),
            createdAtMillis = clock().coerceAtLeast(0L),
        )
        return (listOf(record) + current).also(::write)
    }

    @Synchronized
    fun delete(id: String): List<SavedSignature> {
        require(SAFE_ID.matches(id)) { "The signature identifier is invalid" }
        val current = load()
        val remaining = current.filterNot { it.id == id }
        require(remaining.size != current.size) { "The saved signature could not be found" }
        write(remaining)
        return remaining
    }

    private fun parseSignature(json: JSONObject): SavedSignature {
        val id = json.getString("id")
        require(SAFE_ID.matches(id)) { "The signature identifier is invalid" }
        val strokesJson = json.getJSONArray("strokes")
        require(strokesJson.length() in 1..VisualSignatureRules.MAX_STROKES) { "The signature contains too many strokes" }
        var totalPoints = 0
        val strokes = List(strokesJson.length()) { strokeIndex ->
            val pointsJson = strokesJson.getJSONArray(strokeIndex)
            require(pointsJson.length() in 1..VisualSignatureRules.MAX_POINTS) { "The signature contains an invalid stroke" }
            totalPoints += pointsJson.length()
            require(totalPoints <= VisualSignatureRules.MAX_POINTS) { "The signature contains too many points" }
            SignatureStroke(
                List(pointsJson.length()) { pointIndex ->
                    val pointJson = pointsJson.getJSONArray(pointIndex)
                    require(pointJson.length() == 2) { "The signature point is invalid" }
                    val x = pointJson.getDouble(0).toFloat()
                    val y = pointJson.getDouble(1).toFloat()
                    require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) { "The signature point is invalid" }
                    SignaturePoint(x, y)
                },
            )
        }
        return SavedSignature(
            id = id,
            name = normalizeName(json.getString("name")),
            drawing = VisualSignatureRules.validate(SignatureDrawing(strokes)),
            createdAtMillis = json.getLong("createdAtMillis").also {
                require(it >= 0L) { "The signature date is invalid" }
            },
        )
    }

    private fun write(records: List<SavedSignature>) {
        require(records.size <= MAX_SIGNATURES) { "The signature library limit was exceeded" }
        val payload = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "signatures",
                JSONArray().apply {
                    records.forEach { signature ->
                        put(
                            JSONObject()
                                .put("id", signature.id)
                                .put("name", normalizeName(signature.name))
                                .put("createdAtMillis", signature.createdAtMillis)
                                .put(
                                    "strokes",
                                    JSONArray().apply {
                                        VisualSignatureRules.validate(signature.drawing).strokes.forEach { stroke ->
                                            put(
                                                JSONArray().apply {
                                                    stroke.points.forEach { point -> put(JSONArray().put(point.x).put(point.y)) }
                                                },
                                            )
                                        }
                                    },
                                ),
                        )
                    }
                },
            )
            .toString()
            .toByteArray(Charsets.UTF_8)
        require(payload.size.toLong() in 1..MAX_FILE_BYTES) { "The signature library is too large" }
        require(directory.isDirectory || directory.mkdirs()) { "Could not create the signature library directory" }
        val temporary = File(directory, ".${storageFile.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(payload)
                output.fd.sync()
            }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    storageFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            payload.fill(0)
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun normalizeName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) { "The signature name length is invalid" }
        require(name.none { it == '\u0000' || it.isISOControl() }) { "The signature name contains invalid characters" }
        return name
    }
}
