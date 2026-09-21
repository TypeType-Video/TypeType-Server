package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.downloaderGatewayRoutes(gateway: DownloaderGatewayService) {
    route("/downloader") {
        head { forwardDownloaderRequest(call, gateway) }
        get { forwardDownloaderRequest(call, gateway) }
        post { forwardDownloaderRequest(call, gateway) }
        put { forwardDownloaderRequest(call, gateway) }
        patch { forwardDownloaderRequest(call, gateway) }
        delete { forwardDownloaderRequest(call, gateway) }
        options { forwardDownloaderRequest(call, gateway) }
    }
    route("/downloader/{...}") {
        head { forwardDownloaderRequest(call, gateway) }
        get { forwardDownloaderRequest(call, gateway) }
        post { forwardDownloaderRequest(call, gateway) }
        put { forwardDownloaderRequest(call, gateway) }
        patch { forwardDownloaderRequest(call, gateway) }
        delete { forwardDownloaderRequest(call, gateway) }
        options { forwardDownloaderRequest(call, gateway) }
    }
}

private suspend fun forwardDownloaderRequest(call: ApplicationCall, gateway: DownloaderGatewayService) {
    val requestMethod = call.request.httpMethod.value
    val method = requestMethod
    val path = call.request.path().removePrefix("/downloader").ifBlank { "/" }
    val query = call.request.queryString().takeIf { it.isNotBlank() }
    val requestHeaders = call.request.headers.names().associateWith { call.request.headers[it].orEmpty() }
    val body = if (hasRequestBody(method)) call.receiveText().toByteArray() else null

    if (isSseRequest(path, requestHeaders)) {
        forwardDownloaderSseRequest(call, gateway, method, path, query, requestHeaders, body)
        return
    }

    val forceDownload = shouldForceArtifactDownload(path, query)
    if (path.endsWith("/artifact")) {
        forwardDownloaderArtifactRequest(call, gateway, method, path, query, requestHeaders, forceDownload)
        return
    }

    val response = runCatching { gateway.forward(method, path, query, requestHeaders, body) }
        .getOrElse {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("downloader unavailable"))
            return
        }

    if (isDownloaderStorageFailure(response)) {
        call.respondDownloaderStorageFailure(response)
        return
    }

    response.headers.forEach { (name, value) ->
        if (shouldForwardGatewayResponseHeader(name, forceDownload)) {
            call.response.headers.append(name, value, safeOnly = false)
        }
    }
    if (forceDownload) applyArtifactDownloadHeaders(call, response)

    val status = HttpStatusCode.fromValue(response.status)
    val contentType = if (forceDownload) {
        ContentType.Application.OctetStream
    } else {
        response.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
            ?: ContentType.Application.OctetStream
    }
    call.respondBytes(response.body, contentType = contentType, status = status)
}

private fun hasRequestBody(method: String): Boolean = method == "POST" || method == "PUT" || method == "PATCH"

private fun isSseRequest(path: String, headers: Map<String, String>): Boolean {
    if (path.endsWith("/events")) return true
    val accept = headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
    return accept.contains("text/event-stream", ignoreCase = true)
}
