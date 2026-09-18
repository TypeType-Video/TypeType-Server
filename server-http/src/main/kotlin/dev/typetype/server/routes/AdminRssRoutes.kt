package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.RssFeedEnabledRequest
import dev.typetype.server.models.RssUserPolicyRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RssFeedException
import dev.typetype.server.services.RssFeedManagementService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.adminRssRoutes(service: RssFeedManagementService, authService: AuthService) {
    get("/admin/rss/feeds") {
        call.withRssAdmin(authService) {
            val pageRaw = call.request.queryParameters["page"]
            val limitRaw = call.request.queryParameters["limit"]
            if (
                (pageRaw != null && pageRaw.toIntOrNull() == null) ||
                (limitRaw != null && limitRaw.toIntOrNull() == null)
            ) {
                return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid pagination"))
            }
            val page = pageRaw?.toInt() ?: 1
            val limit = limitRaw?.toInt() ?: 50
            if (page < 1 || limit !in 1..200) {
                return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid pagination"))
            }
            call.respondNoStore(service.adminList(page, limit))
        }
    }
    put("/admin/rss/feeds/{id}/enabled") {
        call.withRssAdmin(authService) {
            val id = call.parameters["id"]
                ?: return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing RSS feed id"))
            val body = runCatching { call.receive<RssFeedEnabledRequest>() }.getOrElse {
                return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respondNoStore(service.adminSetEnabled(id, body.enabled))
        }
    }
    delete("/admin/rss/feeds/{id}") {
        call.withRssAdmin(authService) {
            val id = call.parameters["id"]
                ?: return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing RSS feed id"))
            service.adminDelete(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    put("/admin/rss/users/{id}/enabled") {
        call.withRssAdmin(authService) {
            val userId = call.parameters["id"]
                ?: return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing user id"))
            val body = runCatching { call.receive<RssUserPolicyRequest>() }.getOrElse {
                return@withRssAdmin call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            service.adminSetUserEnabled(userId, body.enabled)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend inline fun io.ktor.server.application.ApplicationCall.withRssAdmin(
    authService: AuthService,
    crossinline block: suspend () -> Unit,
) {
    try {
        withAdminAuth(authService) { block() }
    } catch (error: RssFeedException) {
        respondRssError(error)
    }
}
