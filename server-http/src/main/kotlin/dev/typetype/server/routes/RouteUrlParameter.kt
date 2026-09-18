package dev.typetype.server.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.queryString

internal fun ApplicationCall.urlTailParameter(name: String): String? {
    val pathUrl = parameters.getAll(name)?.joinToString("/") ?: return null
    val normalizedPathUrl = pathUrl.withUrlSchemeSlashes()
    val queryString = request.queryString()
    return if (queryString.isBlank() || "?" in normalizedPathUrl) {
        normalizedPathUrl
    } else {
        "$normalizedPathUrl?$queryString"
    }
}

private fun String.withUrlSchemeSlashes(): String = when {
    startsWith("https:/") && !startsWith("https://") -> "https://" + removePrefix("https:/")
    startsWith("http:/") && !startsWith("http://") -> "http://" + removePrefix("http:/")
    else -> this
}
