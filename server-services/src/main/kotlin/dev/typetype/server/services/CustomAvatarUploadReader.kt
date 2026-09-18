package dev.typetype.server.services

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object CustomAvatarUploadReader {
    suspend fun read(channel: ByteReadChannel, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        channel.toInputStream().use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (output.size() + read > maxBytes) throw AvatarUploadTooLargeException()
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
    }
}

class AvatarUploadTooLargeException : IllegalArgumentException()
