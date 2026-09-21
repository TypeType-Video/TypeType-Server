package dev.typetype.server.routes

import dev.typetype.server.services.SabrDownloadRange
import io.ktor.server.application.ApplicationCall

internal fun ApplicationCall.sabrDownloadRange(): Result<SabrDownloadRange> = runCatching {
    val parts = request.queryParameters["parts"]?.toIntOrNull() ?: 1
    val part = request.queryParameters["part"]?.toIntOrNull() ?: 0
    SabrDownloadRange(part, parts)
}
