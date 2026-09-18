package dev.typetype.server.routes

import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.allowedChannelsRoutes(allowedChannelsService: AllowedChannelsService, authService: AuthService) {
    get("/allowed/channels") {
        call.withJwtAuth(authService) { userId -> call.respond(allowedChannelsService.getChannels(userId)) }
    }
    post("/allowed/channels") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<AllowedChannelItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val role = authService.getUserRole(userId) ?: "user"
            val global = (role == "admin" || role == "moderator") && item.global == true
            call.respond(
                HttpStatusCode.Created,
                allowedChannelsService.addChannel(userId, item.url, item.name, item.thumbnailUrl, global),
            )
        }
    }
    delete("/allowed/channels/{channelUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.urlTailParameter("channelUrl")
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            val role = authService.getUserRole(userId) ?: "user"
            val deleted = allowedChannelsService.deleteChannel(userId, channelUrl, role)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
