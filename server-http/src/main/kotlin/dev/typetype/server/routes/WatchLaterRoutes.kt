package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.UserVideoMetadataRepairService
import dev.typetype.server.services.WatchLaterService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.watchLaterRoutes(watchLaterService: WatchLaterService, authService: AuthService, metadataRepairService: UserVideoMetadataRepairService? = null) {
    get("/watch-later") {
        call.withJwtAuth(authService) { userId ->
            val items = watchLaterService.getAll(userId)
            metadataRepairService?.scheduleWatchLater(call.application, userId)
            call.respond(items)
        }
    }
    post("/watch-later") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<WatchLaterItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, watchLaterService.add(userId, item))
        }
    }
    delete("/watch-later/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl")
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = watchLaterService.delete(userId, videoUrl)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            }
        }
    }
}
