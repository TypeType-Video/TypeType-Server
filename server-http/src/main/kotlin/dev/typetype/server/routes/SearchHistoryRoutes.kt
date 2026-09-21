package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SearchHistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchHistoryBody(val term: String)

fun Route.searchHistoryRoutes(searchHistoryService: SearchHistoryService, authService: AuthService) {
    get("/search-history") {
        call.withJwtAuth(authService) { userId ->
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            if (page < 1) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid page"))
            if (limit !in 1..100) return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            val (items, total) = searchHistoryService.getPage(userId = userId, page = page, limit = limit)
            call.response.headers.append("X-Total-Count", total.toString())
            call.respond(items)
        }
    }
    post("/search-history") {
        call.withJwtAuth(authService) { userId ->
            val body = runCatching { call.receive<SearchHistoryBody>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, searchHistoryService.add(userId, body.term))
        }
    }
    delete("/search-history") {
        call.withJwtAuth(authService) { userId ->
            searchHistoryService.deleteAll(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
