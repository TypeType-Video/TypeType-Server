package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SavedPlaylistRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.SavedPlaylistService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.savedPlaylistRoutes(
    savedPlaylistService: SavedPlaylistService,
    publicPlaylistService: PublicPlaylistService,
    authService: AuthService,
): Unit {
    get("/saved-playlists") {
        call.withJwtAuth(authService) { userId -> call.respond(savedPlaylistService.getAll(userId)) }
    }
    post("/saved-playlists") {
        call.withJwtAuth(authService) { userId ->
            val body = runCatching { call.receive<SavedPlaylistRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val url = body.url.trim()
            if (url.isBlank()) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url"))
            when (val result = publicPlaylistService.getPlaylist(url = url, nextpage = null)) {
                is ExtractionResult.Success -> call.respond(
                    HttpStatusCode.Created,
                    savedPlaylistService.save(userId, result.data.playlist),
                )
                is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
                is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
            }
        }
    }
    delete("/saved-playlists/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val deleted = savedPlaylistService.delete(userId, id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
