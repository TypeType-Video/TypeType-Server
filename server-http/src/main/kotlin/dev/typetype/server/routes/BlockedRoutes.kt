package dev.typetype.server.routes

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.BlockedKeywordItem
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.blockedRoutes(blockedService: BlockedService, authService: AuthService) {
    get("/blocked/channels") {
        call.withJwtAuth(authService) { userId -> call.respond(blockedService.getChannels(userId)) }
    }
    post("/blocked/channels") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<BlockedItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val role = authService.getUserRole(userId) ?: "user"
            val global = (role == "admin" || role == "moderator") && item.global == true
            call.respond(HttpStatusCode.Created, blockedService.addChannel(userId, item.url, item.name, item.thumbnailUrl, global))
        }
    }
    delete("/blocked/channels/{channelUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.urlTailParameter("channelUrl")
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            val role = authService.getUserRole(userId) ?: "user"
            val deleted = blockedService.deleteChannel(userId, channelUrl, role)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    get("/blocked/videos") {
        call.withJwtAuth(authService) { userId -> call.respond(blockedService.getVideos(userId)) }
    }
    post("/blocked/videos") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<BlockedItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val role = authService.getUserRole(userId) ?: "user"
            val global = (role == "admin" || role == "moderator") && item.global == true
            call.respond(HttpStatusCode.Created, blockedService.addVideo(userId, item.url, global))
        }
    }
    delete("/blocked/videos/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val role = authService.getUserRole(userId) ?: "user"
            val deleted = blockedService.deleteVideo(userId, videoUrl, role)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    get("/blocked/keywords") {
        call.withJwtAuth(authService) { userId -> call.respond(blockedService.getKeywords(userId)) }
    }
    post("/blocked/keywords") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<BlockedKeywordItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val keyword = item.keyword.trim()
            if (keyword.isEmpty() || keyword.length > 100) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Keyword must contain 1 to 100 characters"))
            }
            val role = authService.getUserRole(userId) ?: "user"
            val global = (role == "admin" || role == "moderator") && item.global == true
            call.respond(HttpStatusCode.Created, blockedService.addKeyword(userId, keyword, global))
        }
    }
    delete("/blocked/keywords/{keyword...}") {
        call.withJwtAuth(authService) { userId ->
            val keyword = call.urlTailParameter("keyword")
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing keyword"))
            val role = authService.getUserRole(userId) ?: "user"
            val deleted = blockedService.deleteKeyword(userId, keyword, role)
            if (deleted) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
