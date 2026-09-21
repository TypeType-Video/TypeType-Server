package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream

suspend fun forwardDownloaderSseRequest(
    call: ApplicationCall,
    gateway: DownloaderGatewayService,
    method: String,
    path: String,
    query: String?,
    requestHeaders: Map<String, String>,
    body: ByteArray?,
) {
    val upstream = runCatching { gateway.openStream(method, path, query, requestHeaders, body) }
        .getOrElse {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("downloader unavailable"))
            return
        }

    upstream.use { response ->
        response.headers.names().forEach { name ->
            if (shouldForwardGatewayResponseHeader(name)) {
                response.headers(name).forEach { value ->
                    call.response.headers.append(name, value, safeOnly = false)
                }
            }
        }

        val status = HttpStatusCode.fromValue(response.code)
        val contentType = response.header("Content-Type")
            ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
            ?: ContentType.Text.EventStream

        response.body.byteStream().use { input ->
            call.respondOutputStream(contentType = contentType, status = status) {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    write(buffer, 0, read)
                    flush()
                }
            }
        }
    }
}
