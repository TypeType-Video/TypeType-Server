package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.filterAllowed
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.publicPlaylistRoutes(
    playlistService: PublicPlaylistService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/playlist") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]

        val profile = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)?.profile ?: return@get
        when (val result = playlistService.getPlaylist(url = url, nextpage = nextpage)) {
            is ExtractionResult.Success -> call.respond(result.data.filterAllowed(profile))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
