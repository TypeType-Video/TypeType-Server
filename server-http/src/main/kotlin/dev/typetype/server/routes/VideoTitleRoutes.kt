package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.VideoTitleService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
internal data class VideoTitlesResponse(val titles: Map<String, String>)

fun Route.videoTitleRoutes(
    videoTitleService: VideoTitleService,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/videos/titles") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val ids = call.request.queryParameters["ids"]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (ids.isEmpty()) return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'ids' parameter"))
        call.respond(VideoTitlesResponse(videoTitleService.titles(ids)))
    }
}
