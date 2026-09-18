package dev.typetype.server.routes

internal object SabrPlaybackPaths {
    fun manifestPath(sessionId: String): String = "/sabr/playback/$sessionId/manifest"

    fun mediaBasePath(sessionId: String): String = "/api/sabr/playback/$sessionId"
}
