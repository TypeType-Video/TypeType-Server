package dev.typetype.server.services

import java.io.InputStream
import java.io.OutputStream

class SabrDownloadFrameWriter(private val output: OutputStream) {
    private val header = ByteArray(FRAME_HEADER_SIZE)
    private val copyBuffer = ByteArray(COPY_BUFFER_SIZE)

    fun start(): Unit = output.write(MAGIC)

    fun initialization(itag: Int, data: ByteArray) {
        writeHeader(FRAME_INITIALIZATION, itag, 0, data.size.toLong())
        output.write(data)
    }

    fun media(itag: Int, sequence: Int, length: Int, input: InputStream) {
        require(length >= 0) { "Negative SABR frame length" }
        writeHeader(FRAME_MEDIA, itag, sequence, length.toLong())
        var remaining = length
        while (remaining > 0) {
            val read = input.read(copyBuffer, 0, minOf(copyBuffer.size, remaining))
            if (read < 0) error("SABR segment ended before its declared length")
            if (read == 0) continue
            output.write(copyBuffer, 0, read)
            remaining -= read
        }
        check(input.read() == -1) { "SABR segment exceeds its declared length" }
    }

    fun finish() {
        writeHeader(FRAME_COMPLETE, 0, 0, 0)
        output.flush()
    }

    private fun writeHeader(type: Int, itag: Int, sequence: Int, length: Long) {
        header[0] = type.toByte()
        header.putInt(1, itag)
        header.putInt(5, sequence)
        header.putLong(9, length)
        output.write(header)
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        for (index in 0 until Int.SIZE_BYTES) {
            this[offset + index] = (value ushr (24 - index * 8)).toByte()
        }
    }

    private fun ByteArray.putLong(offset: Int, value: Long) {
        for (index in 0 until Long.SIZE_BYTES) {
            this[offset + index] = (value ushr (56 - index * 8)).toByte()
        }
    }

    internal companion object {
        val MAGIC = "TTSABR1\n".encodeToByteArray()
        const val FRAME_INITIALIZATION = 1
        const val FRAME_MEDIA = 2
        const val FRAME_COMPLETE = 3
        const val FRAME_HEADER_SIZE = 17
        const val COPY_BUFFER_SIZE = 256 * 1024
    }
}
