package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.SettingsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private const val MAX_HISTORY_LIMIT = 1000

fun Route.historyRoutes(historyService: HistoryService, authService: AuthService, settingsService: SettingsService? = null) {
    get("/history") {
        call.withJwtAuth(authService) { userId ->
            val q = call.request.queryParameters["q"]
            val fromRaw = call.request.queryParameters["from"]
            val toRaw = call.request.queryParameters["to"]
            val limitRaw = call.request.queryParameters["limit"]
            val offsetRaw = call.request.queryParameters["offset"]
            val from = if (fromRaw == null) null else fromRaw.toLongOrNull()
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid from"))
            val to = if (toRaw == null) null else toRaw.toLongOrNull()
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid to"))
            if (from != null && to != null && from >= to) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("from must be lower than to"))
            }
            val limit = if (limitRaw == null) {
                60
            } else {
                limitRaw.toIntOrNull()?.coerceIn(1, MAX_HISTORY_LIMIT)
                    ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            }
            val offset = if (offsetRaw == null) {
                0
            } else {
                offsetRaw.toIntOrNull()
                    ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid offset"))
            }
            if (offset < 0) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid offset"))
            val (items, total) = historyService.search(userId, q, from, to, limit, offset)
            call.response.headers.append("X-Total-Count", total.toString())
            call.respond(items)
        }
    }
    post("/history") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<HistoryItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (settingsService?.isWatchHistoryDisabled(userId) == true) {
                return@withJwtAuth call.respond(HttpStatusCode.Created, item)
            }
            call.respond(HttpStatusCode.Created, historyService.add(userId, item))
        }
    }
    delete("/history/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val deleted = historyService.delete(userId, id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    delete("/history") {
        call.withJwtAuth(authService) { userId ->
            historyService.deleteAll(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
