package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistReorderRequest
import dev.typetype.server.models.PlaylistReorderResult
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.UserVideoMetadataRepairService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.playlistRoutes(playlistService: PlaylistService, authService: AuthService, metadataRepairService: UserVideoMetadataRepairService? = null) {
    get("/playlists") {
        call.withJwtAuth(authService) { userId ->
            call.respond(playlistService.getAll(userId))
        }
    }
    post("/playlists") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<PlaylistItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, playlistService.create(userId, item))
        }
    }
    get("/playlists/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val playlist = playlistService.getById(userId, id) ?: return@withJwtAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            metadataRepairService?.schedulePlaylists(call.application, userId)
            call.respond(playlist)
        }
    }
    put("/playlists/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val item = runCatching { call.receive<PlaylistItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val updated = playlistService.update(userId, id, item)
            if (updated) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    delete("/playlists/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val deleted = playlistService.delete(userId, id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    post("/playlists/{id}/videos") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val video = runCatching { call.receive<PlaylistVideoItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, playlistService.addVideo(userId, id, video))
        }
    }
    put("/playlists/{id}/reorder") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val body = runCatching { call.receive<PlaylistReorderRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (val result = playlistService.reorder(userId, id, body.order)) {
                PlaylistReorderResult.Success -> call.respond(HttpStatusCode.NoContent)
                PlaylistReorderResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
                is PlaylistReorderResult.InvalidOrder -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            }
        }
    }
    delete("/playlists/{id}/videos/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = playlistService.removeVideo(userId, id, videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
