package dev.typetype.server.routes

import dev.typetype.server.models.AdminUserAllowListItem
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.adminUserAllowListRoutes(
    authService: AuthService,
    userLookupService: AdminUserLookupService,
    allowedChannelsService: AllowedChannelsService,
    allowedPlaylistsService: AllowedPlaylistsService,
) {
    get("/admin/users/{id}/allow-list") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val user = userLookupService.get(id)
                ?: return@withAdminAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            call.respond(
                AdminUserAllowListItem(
                    user = user,
                    globalChannels = allowedChannelsService.getGlobalChannels(),
                    userChannels = allowedChannelsService.getUserChannels(id),
                    globalPlaylists = allowedPlaylistsService.getGlobalPlaylists(),
                    userPlaylists = allowedPlaylistsService.getUserPlaylists(id),
                )
            )
        }
    }
}
