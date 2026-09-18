package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.adminUserSearchRoutes(authService: AuthService, userLookupService: AdminUserLookupService) {
    get("/admin/users/search") {
        call.withAdminAuth(authService) { _ ->
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            if (query.isBlank()) return@withAdminAuth call.respond(emptyList<Any>())
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            if (limit !in 1..50) {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            }
            call.respond(userLookupService.search(query, limit))
        }
    }
}
