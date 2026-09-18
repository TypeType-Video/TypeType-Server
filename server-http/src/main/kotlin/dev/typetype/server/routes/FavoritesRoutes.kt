package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.UserVideoMetadataRepairService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.favoritesRoutes(favoritesService: FavoritesService, authService: AuthService, metadataRepairService: UserVideoMetadataRepairService? = null) {
    get("/favorites") {
        call.withJwtAuth(authService) { userId ->
            val items = favoritesService.getAll(userId)
            metadataRepairService?.scheduleFavorites(call.application, userId)
            call.respond(items)
        }
    }
    get("/favorites/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val favorite = favoritesService.get(userId, videoUrl)
            if (favorite == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found")) else call.respond(favorite)
        }
    }
    post("/favorites/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            call.respond(HttpStatusCode.Created, favoritesService.add(userId, videoUrl))
        }
    }
    delete("/favorites/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = favoritesService.delete(userId, videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
