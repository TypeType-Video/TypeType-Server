package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AudioOnlyStreamKind
import dev.typetype.server.services.ProxyService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream

internal suspend fun ApplicationCall.respondAudioOnlyHead(
    source: AudioOnlySourceResolution,
    proxyService: ProxyService,
): Unit {
    when (val result = source.result) {
        is ExtractionResult.Success -> {
            response.headers.append(HttpHeaders.CacheControl, "no-store")
            val length = result.data.stream.contentLength
                .takeIf { it > 0 && result.data.kind == AudioOnlyStreamKind.Progressive }
                ?: proxyService.probeAudioOnlyContentLength(result.data.stream.url)
            val range = length?.let { parseAudioOnlyByteRange(request.headers[HttpHeaders.Range], it) }
            if (range is AudioOnlyByteRange.Satisfiable) {
                response.headers.append(HttpHeaders.AcceptRanges, "bytes")
                response.headers.append(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/${range.total}")
                respondOutputStream(
                    containerMime(result.data.stream.mimeType),
                    HttpStatusCode.PartialContent,
                    range.last - range.first + 1L,
                ) {}
            } else if (range is AudioOnlyByteRange.Unsatisfiable) {
                response.headers.append(HttpHeaders.AcceptRanges, "bytes")
                response.headers.append(HttpHeaders.ContentRange, "bytes */${range.total}")
                respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            } else {
                response.headers.append(HttpHeaders.AcceptRanges, if (length == null) "none" else "bytes")
                respondOutputStream(containerMime(result.data.stream.mimeType), HttpStatusCode.OK, length ?: 0L) {}
            }
        }
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
    }
}
