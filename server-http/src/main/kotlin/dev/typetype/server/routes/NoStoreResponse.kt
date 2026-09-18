package dev.typetype.server.routes

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.respondNoStore(value: Any): Unit {
    response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0", safeOnly = false)
    respond(value)
}
