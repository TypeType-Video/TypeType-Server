package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminManagedAccessService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.adminManagedAccessRoutes(authService: AuthService, service: AdminManagedAccessService) {
    get("/admin/users/managed-access") {
        call.withAdminAuth(authService) { _ ->
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            if (limit !in 1..200) {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            }
            val page = call.request.queryParameters["page"]
            if (page != null && page.toIntOrNull()?.let { it >= 0 } != true) {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid page"))
            }
            call.respond(service.list(limit = limit, page = page))
        }
    }
}
