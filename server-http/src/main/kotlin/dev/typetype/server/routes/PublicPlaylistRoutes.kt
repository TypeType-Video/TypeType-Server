package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedContentProfile
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.filterBlocked
import dev.typetype.server.services.filterFocus
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.publicPlaylistRoutes(
    playlistService: PublicPlaylistService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    blockedService: BlockedService? = null,
    focusService: FocusService? = null,
) {
    get("/playlist") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]

        val access = call.accessProfileOrRespond(
            authService,
            accessControlService,
            adminSettingsService,
        ) ?: return@get
        val blocked = access.userId?.let { blockedService?.profileFor(it) } ?: BlockedContentProfile.empty
        val focus = access.userId?.let { focusService?.profileFor(it) }
        when (val result = playlistService.getPlaylist(url = url, nextpage = nextpage)) {
            is ExtractionResult.Success -> call.respond(
                result.data.filterAllowed(access.profile).filterBlocked(blocked).filterFocus(focus),
            )
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, youtube = false)
        }
    }
}
