package dev.typetype.server.services

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

object PipePipeBackupUploadWriter {
    suspend fun writeWithLimit(channel: ByteReadChannel, target: Path, maxBytes: Long): Long = withContext(Dispatchers.IO) {
        var written = 0L
        channel.toInputStream().use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    written += read
                    if (written > maxBytes) throw IllegalArgumentException("Backup file too large")
                    output.write(buffer, 0, read)
                }
            }
        }
        written
    }
}
