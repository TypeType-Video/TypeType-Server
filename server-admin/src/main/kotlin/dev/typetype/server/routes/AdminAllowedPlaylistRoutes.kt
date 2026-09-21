package dev.typetype.server.routes

import dev.typetype.server.models.AllowedPlaylistItem
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.adminAllowedPlaylistRoutes(authService: AuthService, allowedPlaylistsService: AllowedPlaylistsService) {
    get("/admin/allowed/playlists") {
        call.withAdminAuth(authService) { _ -> call.respond(allowedPlaylistsService.getGlobalPlaylists()) }
    }
    post("/admin/allowed/playlists") {
        call.withAdminAuth(authService) { adminId ->
            val item = runCatching { call.receive<AllowedPlaylistItem>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, allowedPlaylistsService.addPlaylist(adminId, item, global = true))
        }
    }
    delete("/admin/allowed/playlists/{playlistUrl...}") {
        call.withAdminAuth(authService) { adminId ->
            val url = call.urlTailParameter("playlistUrl")
                ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing playlistUrl"))
            val deleted = allowedPlaylistsService.deletePlaylist(adminId, url, global = true)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
