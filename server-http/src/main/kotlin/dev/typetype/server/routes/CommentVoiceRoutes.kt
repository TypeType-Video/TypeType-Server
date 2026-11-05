package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.CommentVoiceAudioService
import dev.typetype.server.services.isYoutubeUrl
import dev.typetype.server.services.youtubeVideoId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.commentVoiceRoutes(
    voiceService: CommentVoiceAudioService,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/comments/voice") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val videoId = url.takeIf(::isYoutubeUrl)?.let(::youtubeVideoId)
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Not a YouTube voice reply"))
        val audio = voiceService.audio(videoId)
            ?: return@get call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("Voice reply audio is not available", "no_playable_streams"),
            )
        call.response.header(HttpHeaders.CacheControl, "private, max-age=3600")
        call.respondBytes(audio.bytes, ContentType.parse(audio.mimeType))
    }
}
