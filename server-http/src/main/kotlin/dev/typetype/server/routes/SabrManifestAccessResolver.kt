package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AudioOnlyMediaTokenResult
import dev.typetype.server.services.AudioOnlyMediaTokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal class SabrManifestAccessResolver(
    private val audioOnlyTokenService: AudioOnlyMediaTokenService?,
) {
    suspend fun resolve(call: ApplicationCall, videoId: String): SabrManifestAccess? {
        val raw = call.request.queryParameters["audioToken"] ?: return SabrManifestAccess.RequiresAuth
        val tokenService = audioOnlyTokenService
            ?: return call.unauthorizedAudioToken("Invalid audio-only token")
        val token = when (val result = tokenService.verify(raw)) {
            is AudioOnlyMediaTokenResult.Valid -> result.token
            AudioOnlyMediaTokenResult.Expired -> return call.unauthorizedAudioToken("Audio-only token expired")
            AudioOnlyMediaTokenResult.Invalid -> return call.unauthorizedAudioToken("Invalid audio-only token")
        }
        if (!token.videoUrl.matchesVideoId(videoId)) {
            return call.unauthorizedAudioToken("Invalid audio-only token")
        }
        return SabrManifestAccess.AudioOnlyToken(token)
    }

    private suspend fun ApplicationCall.unauthorizedAudioToken(message: String): SabrManifestAccess? {
        respond(HttpStatusCode.Unauthorized, ErrorResponse(message))
        return null
    }

    private fun String.matchesVideoId(videoId: String): Boolean {
        val found = Regex("""(?:[?&]v=|/shorts/|youtu\.be/)([A-Za-z0-9_-]{6,})""").find(this)?.groupValues?.get(1)
        return found == videoId
    }
}
