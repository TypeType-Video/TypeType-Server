package dev.typetype.server.routes

import dev.typetype.server.services.DownloaderGatewayResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

fun shouldForwardGatewayResponseHeader(name: String, forceDownload: Boolean = false): Boolean {
    val lower = name.lowercase()
    val blocked = lower == "content-length" || lower == "transfer-encoding" || lower == "connection"
    if (blocked) return false
    if (!forceDownload) return true
    return lower != "content-type" &&
        lower != "content-disposition" &&
        lower != "x-content-type-options" &&
        lower != "cache-control" &&
        lower != "pragma" &&
        lower != "expires"
}

fun shouldForceArtifactDownload(path: String, query: String?): Boolean {
    if (!path.endsWith("/artifact")) return false
    val raw = queryParam(query, "download") ?: return true
    val value = raw.lowercase()
    return value != "0" && value != "false" && value != "no"
}

fun applyArtifactDownloadHeaders(call: ApplicationCall, response: DownloaderGatewayResponse) {
    call.response.headers.append(HttpHeaders.ContentDisposition, attachmentDisposition(response), safeOnly = false)
    call.response.headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
    call.response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0", safeOnly = false)
    call.response.headers.append(HttpHeaders.Pragma, "no-cache", safeOnly = false)
    call.response.headers.append(HttpHeaders.Expires, "0", safeOnly = false)
}

private fun queryParam(query: String?, key: String): String? {
    if (query.isNullOrBlank()) return null
    return query.split('&')
        .asSequence()
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.firstOrNull() == key }
        ?.getOrNull(1)
}

private fun attachmentDisposition(response: DownloaderGatewayResponse): String {
    val current = response.headers.firstOrNull { it.first.equals(HttpHeaders.ContentDisposition, ignoreCase = true) }
        ?.second
        .orEmpty()
    if (current.isBlank()) return "attachment"
    if (current.startsWith("attachment", ignoreCase = true)) return current
    return current.replaceFirst(Regex("^inline", RegexOption.IGNORE_CASE), "attachment")
}
