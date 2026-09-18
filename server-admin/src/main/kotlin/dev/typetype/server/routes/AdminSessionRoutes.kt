package dev.typetype.server.routes

import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AuthService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.adminSessionRoutes(authService: AuthService, activeSessionService: ActiveSessionService): Unit {
    get("/admin/sessions") {
        call.withAdminAuth(authService) {
            call.respond(activeSessionService.list())
        }
    }
}
