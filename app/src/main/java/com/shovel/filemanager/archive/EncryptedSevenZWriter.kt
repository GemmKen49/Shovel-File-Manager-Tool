package com.shovel.filemanager.archive

import com.shovel.filemanager.operations.OperationContext
import org.tukaani.xz.FinishableOutputStream
import org.tukaani.xz.FinishableWrapperOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Writes 7z archives whose contents are encrypted with AES-256 (the same "7zAES" scheme 7-Zip uses).
 *
 * Layout: signature header, one solid encrypted stream with every file, then the header.
 * With [hideNames] the header itself is encrypted too, so file names are not readable without the password.
 * Commons Compress can read 7z AES archives but cannot write them, which is why this exists.
 */
internal class EncryptedSevenZWriter(
    private val file: File,
    private val password: CharArray,
    private val hideNames: Boolean,
    private val compressionLevel: Int,
    private val maxDepth: Int,
) {
    private class Entry(
        val name: String,
        val directory: Boolean,
        val size: Long,
        val crc: Long,
        val modifiedMillis: Long,
    ) {
        val hasStream: Boolean get() = !directory && size > 0L
    }

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    /** AES-256-CBC without padding; the tail is zero padded to a full block when finished. */
    private class AesCbcOutputStream(private val out: OutputStream, key: ByteArray, iv: ByteArray) : OutputStream() {
        private val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
        private var total = 0L

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            total += len
            cipher.update(b, off, len)?.let { if (it.isNotEmpty()) out.write(it) }
        }

        fun finishPadding() {
            val remainder = (total % AES_BLOCK).toInt()
            if (remainder != 0) cipher.update(ByteArray(AES_BLOCK - remainder))?.let { if (it.isNotEmpty()) out.write(it) }
            cipher.doFinal()?.let { if (it.isNotEmpty()) out.write(it) }
        }

        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    private val entries = ArrayList<Entry>()
    private val random = SecureRandom()

    private lateinit var raf: RandomAccessFile
    private lateinit var buffered: BufferedOutputStream
    private lateinit var packed: CountingOutputStream
    private lateinit var key: ByteArray

    private var aes: AesCbcOutputStream? = null
    private var compressedCounter: CountingOutputStream? = null
    private var lzma: FinishableOutputStream? = null
    private var sink: OutputStream? = null
    private var contentIv = ByteArray(0)
    private var dictionaryByte = -1
    private var rawBytes = 0L

    suspend fun write(sources: List<File>, operation: OperationContext?) {
        key = deriveKey(password)
        try {
            RandomAccessFile(file, "rw").use { output ->
                raf = output
                output.setLength(0L)
                output.write(ByteArray(SIGNATURE_HEADER_SIZE))
                buffered = BufferedOutputStream(RandomAccessOutputStream(output), IO_BUFFER)
                packed = CountingOutputStream(buffered)

                sources.forEach { source -> addEntry(source, source.name, 0, operation) }

                lzma?.finish()
                aes?.finishPadding()
                buffered.flush()
                val dataPackSize = packed.count

                val header = buildHeader(dataPackSize)
                if (hideNames) {
                    val headerIv = ByteArray(AES_BLOCK).also(random::nextBytes)
                    val encrypted = ByteArrayOutputStream()
                    AesCbcOutputStream(encrypted, key, headerIv).also { stream ->
                        stream.write(header)
                        stream.finishPadding()
                    }
                    val encryptedBytes = encrypted.toByteArray()
                    output.write(encryptedBytes)
                    val encodedHeader = buildEncodedHeader(dataPackSize, encryptedBytes.size.toLong(), header, headerIv)
                    writeNextHeader(output, dataPackSize + encryptedBytes.size, encodedHeader)
                } else {
                    writeNextHeader(output, dataPackSize, header)
                }
            }
        } finally {
            key.fill(0)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reading the sources

    private suspend fun addEntry(source: File, archiveName: String, depth: Int, operation: OperationContext?) {
        require(depth <= maxDepth) { "Per gilus aplankų medis" }
        operation?.checkpoint()
        if (source.isDirectory) {
            entries += Entry(archiveName, true, 0L, 0L, source.lastModified())
            operation?.progress(itemDelta = 1, currentName = source.name)
            val children = source.listFiles() ?: throw SecurityException("Aplankas neperskaitomas")
            children.forEach { child -> addEntry(child, "$archiveName/${child.name}", depth + 1, operation) }
            return
        }
        val crc = CRC32()
        var size = 0L
        source.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                operation?.checkpoint()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                ensureSink().write(buffer, 0, read)
                crc.update(buffer, 0, read)
                size += read
                rawBytes += read
                operation?.progress(byteDelta = read.toLong(), currentName = source.name)
            }
        }
        entries += Entry(archiveName, false, size, crc.value, source.lastModified())
        operation?.progress(itemDelta = 1, currentName = source.name)
    }

    /** The encrypted stream is only created once the first byte of file data arrives. */
    private fun ensureSink(): OutputStream {
        sink?.let { return it }
        contentIv = ByteArray(AES_BLOCK).also(random::nextBytes)
        val encryptor = AesCbcOutputStream(packed, key, contentIv)
        aes = encryptor
        val counter = CountingOutputStream(encryptor)
        compressedCounter = counter
        val created: OutputStream = if (compressionLevel > 0) {
            val options = LZMA2Options(minOf(compressionLevel, MAX_LZMA_PRESET))
            dictionaryByte = lzma2PropertyByte(options.dictSize)
            options.getOutputStream(FinishableWrapperOutputStream(counter)).also { lzma = it }
        } else {
            counter
        }
        sink = created
        return created
    }

    // ---------------------------------------------------------------------------------------------
    // Building the header

    private fun buildHeader(dataPackSize: Long): ByteArray {
        val streamEntries = entries.filter(Entry::hasStream)
        val header = ByteArrayOutputStream()
        header.write(K_HEADER)
        if (streamEntries.isNotEmpty()) {
            header.write(K_MAIN_STREAMS_INFO)
            header.write(buildMainStreamsInfo(dataPackSize, streamEntries))
        }
        header.write(K_FILES_INFO)
        header.write(buildFilesInfo())
        header.write(K_END)
        return header.toByteArray()
    }

    private fun buildMainStreamsInfo(dataPackSize: Long, streamEntries: List<Entry>): ByteArray {
        val compressedSize = compressedCounter?.count ?: 0L
        val info = ByteArrayOutputStream()
        // PackInfo
        info.write(K_PACK_INFO)
        info.writeNumber(0L)
        info.writeNumber(1L)
        info.write(K_SIZE)
        info.writeNumber(dataPackSize)
        info.write(K_END)
        // Coders
        info.write(K_UNPACK_INFO)
        info.write(K_FOLDER)
        info.writeNumber(1L)
        info.write(0) // not external
        if (dictionaryByte >= 0) {
            info.writeNumber(2L)
            info.write(CODER_FLAGS_LZMA2)
            info.write(LZMA2_ID)
            info.writeNumber(1L)
            info.write(dictionaryByte)
            writeAesCoder(info, contentIv)
            info.writeNumber(0L) // bind pair: the compressor reads what the decryptor produces
            info.writeNumber(1L)
            info.write(K_CODERS_UNPACK_SIZE)
            info.writeNumber(rawBytes)
            info.writeNumber(compressedSize)
        } else {
            info.writeNumber(1L)
            writeAesCoder(info, contentIv)
            info.write(K_CODERS_UNPACK_SIZE)
            info.writeNumber(rawBytes)
        }
        info.write(K_END)
        // SubStreams: one stream per file
        info.write(K_SUBSTREAMS_INFO)
        info.write(K_NUM_UNPACK_STREAM)
        info.writeNumber(streamEntries.size.toLong())
        if (streamEntries.size > 1) {
            info.write(K_SIZE)
            streamEntries.dropLast(1).forEach { info.writeNumber(it.size) }
        }
        info.write(K_CRC)
        info.write(1) // all defined
        streamEntries.forEach { info.writeIntLe(it.crc) }
        info.write(K_END)
        info.write(K_END)
        return info.toByteArray()
    }

    private fun buildFilesInfo(): ByteArray {
        val count = entries.size
        val info = ByteArrayOutputStream()
        info.writeNumber(count.toLong())
        val emptyStream = entries.map { !it.hasStream }
        if (emptyStream.any { it }) {
            val bits = bitVector(emptyStream)
            info.write(K_EMPTY_STREAM)
            info.writeNumber(bits.size.toLong())
            info.write(bits)
            val emptyEntries = entries.filter { !it.hasStream }
            if (emptyEntries.any { !it.directory }) {
                val fileBits = bitVector(emptyEntries.map { !it.directory })
                info.write(K_EMPTY_FILE)
                info.writeNumber(fileBits.size.toLong())
                info.write(fileBits)
            }
        }
        val names = ByteArrayOutputStream()
        entries.forEach { entry ->
            names.write(entry.name.toByteArray(Charsets.UTF_16LE))
            names.write(0)
            names.write(0)
        }
        info.write(K_NAMES)
        info.writeNumber(names.size() + 1L)
        info.write(0) // not external
        info.write(names.toByteArray())

        val times = ByteArrayOutputStream()
        entries.forEach { times.writeLongLe((it.modifiedMillis + EPOCH_DIFFERENCE_MILLIS) * 10_000L) }
        info.write(K_MTIME)
        info.writeNumber(times.size() + 2L)
        info.write(1) // all defined
        info.write(0) // not external
        info.write(times.toByteArray())

        val attributes = ByteArrayOutputStream()
        entries.forEach { attributes.writeIntLe(if (it.directory) ATTRIBUTE_DIRECTORY else ATTRIBUTE_ARCHIVE) }
        info.write(K_WIN_ATTRIBUTES)
        info.writeNumber(attributes.size() + 2L)
        info.write(1)
        info.write(0)
        info.write(attributes.toByteArray())
        info.write(K_END)
        return info.toByteArray()
    }

    /** The real header is stored encrypted; this small block tells readers where it is and how to decrypt it. */
    private fun buildEncodedHeader(dataPackSize: Long, encryptedSize: Long, header: ByteArray, iv: ByteArray): ByteArray {
        val info = ByteArrayOutputStream()
        info.write(K_ENCODED_HEADER)
        info.write(K_PACK_INFO)
        info.writeNumber(dataPackSize)
        info.writeNumber(1L)
        info.write(K_SIZE)
        info.writeNumber(encryptedSize)
        info.write(K_END)
        info.write(K_UNPACK_INFO)
        info.write(K_FOLDER)
        info.writeNumber(1L)
        info.write(0)
        info.writeNumber(1L)
        writeAesCoder(info, iv)
        info.write(K_CODERS_UNPACK_SIZE)
        info.writeNumber(header.size.toLong())
        info.write(K_CRC)
        info.write(1)
        info.writeIntLe(crc32(header))
        info.write(K_END)
        info.write(K_END)
        return info.toByteArray()
    }

    private fun writeAesCoder(out: ByteArrayOutputStream, iv: ByteArray) {
        out.write(CODER_FLAGS_AES)
        out.write(AES_ID)
        out.writeNumber((2 + iv.size).toLong())
        out.write(NUM_CYCLES_POWER or 0x40) // an IV is present, no salt
        out.write(iv.size - 1)
        out.write(iv)
    }

    private fun writeNextHeader(output: RandomAccessFile, nextHeaderOffset: Long, nextHeader: ByteArray) {
        output.write(nextHeader)
        val start = ByteArrayOutputStream()
        start.writeLongLe(nextHeaderOffset)
        start.writeLongLe(nextHeader.size.toLong())
        start.writeIntLe(crc32(nextHeader))
        val startBytes = start.toByteArray()
        val signature = ByteArrayOutputStream()
        signature.write(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x00, 0x04))
        signature.writeIntLe(crc32(startBytes))
        signature.write(startBytes)
        output.seek(0L)
        output.write(signature.toByteArray())
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers

    private class RandomAccessOutputStream(private val raf: RandomAccessFile) : OutputStream() {
        override fun write(b: Int) = raf.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = raf.write(b, off, len)
    }

    private fun bitVector(flags: List<Boolean>): ByteArray {
        val bytes = ByteArray((flags.size + 7) / 8)
        flags.forEachIndexed { index, set ->
            if (set) bytes[index / 8] = (bytes[index / 8].toInt() or (0x80 ushr (index % 8))).toByte()
        }
        return bytes
    }

    private fun crc32(bytes: ByteArray): Long = CRC32().also { it.update(bytes, 0, bytes.size) }.value

    private fun ByteArrayOutputStream.writeNumber(value: Long) {
        var first = 0
        var mask = 0x80
        var extra = 0
        while (extra < 8) {
            if (value < (1L shl (7 * (extra + 1)))) {
                first = first or (value ushr (8 * extra)).toInt()
                break
            }
            first = first or mask
            mask = mask ushr 1
            extra += 1
        }
        write(first and 0xFF)
        var remaining = value
        repeat(extra) {
            write((remaining and 0xFF).toInt())
            remaining = remaining ushr 8
        }
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Long) {
        for (shift in 0 until 32 step 8) write(((value ushr shift) and 0xFF).toInt())
    }

    private fun ByteArrayOutputStream.writeLongLe(value: Long) {
        for (shift in 0 until 64 step 8) write(((value ushr shift) and 0xFF).toInt())
    }

    /** 7-Zip's key derivation: SHA-256 over the UTF-16LE password and a counter, 2^19 times. */
    private fun deriveKey(password: CharArray): ByteArray {
        val passwordBytes = ByteArray(password.size * 2)
        password.forEachIndexed { index, char ->
            passwordBytes[2 * index] = char.code.toByte()
            passwordBytes[2 * index + 1] = (char.code shr 8).toByte()
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val counter = ByteArray(8)
            val rounds = 1L shl NUM_CYCLES_POWER
            var round = 0L
            while (round < rounds) {
                digest.update(passwordBytes)
                digest.update(counter)
                var index = 0
                while (index < counter.size) {
                    counter[index] = (counter[index] + 1).toByte()
                    if (counter[index] != 0.toByte()) break
                    index += 1
                }
                round += 1
            }
            return digest.digest()
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun lzma2PropertyByte(dictionarySize: Int): Int {
        val lead = Integer.numberOfLeadingZeros(dictionarySize)
        val secondBit = (dictionarySize ushr (30 - lead)) - 2
        return (19 - lead) * 2 + secondBit
    }

    private companion object {
        const val SIGNATURE_HEADER_SIZE = 32
        const val AES_BLOCK = 16
        const val NUM_CYCLES_POWER = 19
        const val MAX_LZMA_PRESET = 6
        const val IO_BUFFER = 64 * 1_024
        const val COPY_BUFFER = 256 * 1_024
        const val EPOCH_DIFFERENCE_MILLIS = 11_644_473_600_000L
        const val ATTRIBUTE_DIRECTORY = 0x10L
        const val ATTRIBUTE_ARCHIVE = 0x20L

        const val K_END = 0x00
        const val K_HEADER = 0x01
        const val K_MAIN_STREAMS_INFO = 0x04
        const val K_FILES_INFO = 0x05
        const val K_PACK_INFO = 0x06
        const val K_UNPACK_INFO = 0x07
        const val K_SUBSTREAMS_INFO = 0x08
        const val K_SIZE = 0x09
        const val K_CRC = 0x0A
        const val K_FOLDER = 0x0B
        const val K_CODERS_UNPACK_SIZE = 0x0C
        const val K_NUM_UNPACK_STREAM = 0x0D
        const val K_EMPTY_STREAM = 0x0E
        const val K_EMPTY_FILE = 0x0F
        const val K_NAMES = 0x11
        const val K_MTIME = 0x14
        const val K_WIN_ATTRIBUTES = 0x15
        const val K_ENCODED_HEADER = 0x17

        const val CODER_FLAGS_LZMA2 = 0x21
        const val LZMA2_ID = 0x21
        const val CODER_FLAGS_AES = 0x24
        val AES_ID = byteArrayOf(0x06, 0xF1.toByte(), 0x07, 0x01)
    }
}
