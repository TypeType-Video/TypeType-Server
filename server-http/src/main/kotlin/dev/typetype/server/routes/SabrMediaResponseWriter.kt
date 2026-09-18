package dev.typetype.server.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal suspend fun ApplicationCall.respondSabrMediaBytes(mimeType: String, body: ByteArray): Unit {
    val total = body.size.toLong()
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    when (val range = parseAudioOnlyByteRange(request.headers[HttpHeaders.Range], total)) {
        is AudioOnlyByteRange.Satisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/${range.total}")
            respondBytes(
                body.copyOfRange(range.first.toInt(), (range.last + 1L).toInt()),
                containerMime(mimeType),
                HttpStatusCode.PartialContent,
            )
        }
        is AudioOnlyByteRange.Unsatisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes */${range.total}")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        null -> respondBytes(body, containerMime(mimeType), HttpStatusCode.OK)
    }
}

internal suspend fun ApplicationCall.respondSabrMediaStream(
    mimeType: String,
    total: Long,
    openStream: () -> InputStream,
    onOpened: () -> Unit,
): Unit {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    when (val range = parseAudioOnlyByteRange(request.headers[HttpHeaders.Range], total)) {
        is AudioOnlyByteRange.Satisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/${range.total}")
            stream(openStream, onOpened, range.first, range.last - range.first + 1L, HttpStatusCode.PartialContent, mimeType)
        }
        is AudioOnlyByteRange.Unsatisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes */${range.total}")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        null -> stream(openStream, onOpened, 0L, total, HttpStatusCode.OK, mimeType)
    }
}

private suspend fun ApplicationCall.stream(
    openStream: () -> InputStream,
    onOpened: () -> Unit,
    offset: Long,
    length: Long,
    status: HttpStatusCode,
    mimeType: String,
): Unit = respondOutputStream(containerMime(mimeType), status, length) {
    openStream().use { input ->
        runInterruptible(Dispatchers.IO) {
            input.skipNBytes(offset)
            onOpened()
            copyExactly(input, length)
        }
    }
}

private fun OutputStream.copyExactly(input: InputStream, length: Long) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var remaining = length
    while (remaining > 0L) {
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) throw EOFException("SABR media segment ended before its declared length")
        write(buffer, 0, read)
        remaining -= read
    }
}

private const val COPY_BUFFER_SIZE = 64 * 1024
