package dev.typetype.server.services

import java.io.EOFException
import java.io.InputStream

internal class DeliveredTail(capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var next = 0
    var size = 0
        private set

    fun record(bytes: ByteArray, offset: Int, count: Int) {
        var index = offset
        var remaining = count
        if (remaining > buffer.size) {
            index += remaining - buffer.size
            remaining = buffer.size
        }
        while (remaining > 0) {
            val chunk = minOf(remaining, buffer.size - next)
            System.arraycopy(bytes, index, buffer, next, chunk)
            next = (next + chunk) % buffer.size
            index += chunk
            remaining -= chunk
        }
        size = minOf(buffer.size, size + count)
    }

    fun matches(input: InputStream): Boolean {
        val replay = ByteArray(size)
        var read = 0
        while (read < replay.size) {
            val count = input.read(replay, read, replay.size - read)
            if (count < 0) throw EOFException("Incomplete proxy resume overlap")
            read += count
        }
        val start = (next - size + buffer.size) % buffer.size
        return replay.indices.all { replay[it] == buffer[(start + it) % buffer.size] }
    }
}
