package dev.typetype.server.routes

import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.AllowedPlaylistItem
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post

internal fun Route.adminUserAllowedRoutes(
    authService: AuthService,
    userLookupService: AdminUserLookupService,
    allowedChannelsService: AllowedChannelsService,
    allowedPlaylistsService: AllowedPlaylistsService,
) {
    post("/admin/users/{id}/allowed/channels") {
        call.withAdminAuth(authService) { _ -> call.addUserChannel(userLookupService, allowedChannelsService) }
    }
    delete("/admin/users/{id}/allowed/channels/{channelUrl...}") {
        call.withAdminAuth(authService) { _ -> call.deleteUserChannel(userLookupService, allowedChannelsService) }
    }
    post("/admin/users/{id}/allowed/playlists") {
        call.withAdminAuth(authService) { _ -> call.addUserPlaylist(userLookupService, allowedPlaylistsService) }
    }
    delete("/admin/users/{id}/allowed/playlists/{playlistUrl...}") {
        call.withAdminAuth(authService) { _ -> call.deleteUserPlaylist(userLookupService, allowedPlaylistsService) }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.addUserChannel(users: AdminUserLookupService, service: AllowedChannelsService) {
    val id = checkedUserId(users) ?: return
    val item = runCatching { receive<AllowedChannelItem>() }.getOrElse {
        return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
    }
    respond(HttpStatusCode.Created, service.addChannel(id, item.url, item.name, item.thumbnailUrl, global = false))
}

private suspend fun io.ktor.server.application.ApplicationCall.deleteUserChannel(users: AdminUserLookupService, service: AllowedChannelsService) {
    val id = checkedUserId(users) ?: return
    val url = urlTailParameter("channelUrl") ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
    if (service.deleteChannel(id, url, "user")) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
}

private suspend fun io.ktor.server.application.ApplicationCall.addUserPlaylist(users: AdminUserLookupService, service: AllowedPlaylistsService) {
    val id = checkedUserId(users) ?: return
    val item = runCatching { receive<AllowedPlaylistItem>() }.getOrElse {
        return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
    }
    respond(HttpStatusCode.Created, service.addPlaylist(id, item, global = false))
}

private suspend fun io.ktor.server.application.ApplicationCall.deleteUserPlaylist(users: AdminUserLookupService, service: AllowedPlaylistsService) {
    val id = checkedUserId(users) ?: return
    val url = urlTailParameter("playlistUrl") ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Missing playlistUrl"))
    if (service.deletePlaylist(id, url, global = false)) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
}

private suspend fun io.ktor.server.application.ApplicationCall.checkedUserId(users: AdminUserLookupService): String? {
    val id = parameters["id"] ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id")).let { null }
    if (users.get(id) == null) return respond(HttpStatusCode.NotFound, ErrorResponse("User not found")).let { null }
    return id
}
