package dev.typetype.server.routes

import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.metricPath
import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respond

internal object SabrPlaybackPaths {
    fun manifestPath(sessionId: String): String = "/sabr/playback/$sessionId/manifest"

    fun mediaBasePath(sessionId: String): String = "/api/sabr/playback/$sessionId"
}

internal fun redactedSabrPath(path: String, sessionId: String): String {
    if (sessionId.isEmpty()) return metricPath(path)
    val segments = path.split('/')
    return if (segments.contains(sessionId)) {
        segments.joinToString("/") { if (it == sessionId) "{sessionId}" else it }
    } else {
        metricPath(path)
    }
}

internal suspend fun ApplicationCall.respondMissingSabrSession(sessionId: String): Nothing? {
    val path = redactedSabrPath(request.path(), sessionId)
    PlaybackTraceLog.record("sabr_session_not_found", "path=$path sessionPrefix=${sessionId.take(8)}")
    respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
    return null
}
