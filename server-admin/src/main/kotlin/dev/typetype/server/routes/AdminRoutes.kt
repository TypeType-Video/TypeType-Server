package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.AdminUsersPageItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.UserAdminService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
private data class RoleBody(val role: String)

@Serializable
private data class ResetTokenResponse(val resetToken: String)

private val adminRouteLog = LoggerFactory.getLogger("AdminRoutes")

fun Route.adminRoutes(
    authService: AuthService,
    userAdminService: UserAdminService,
    passwordResetService: PasswordResetService,
    adminSettingsService: AdminSettingsService,
) {
    get("/admin/users") {
        call.withAdminAuth(authService) { _ ->
            val pageRaw = call.request.queryParameters["page"]
            val limitRaw = call.request.queryParameters["limit"]
            if (pageRaw == null && limitRaw == null) {
                return@withAdminAuth call.respond(userAdminService.listUsers())
            }
            if (pageRaw != null && pageRaw.toIntOrNull() == null) {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid page"))
            }
            if (limitRaw != null && limitRaw.toIntOrNull() == null) {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            }
            val page = pageRaw?.toIntOrNull() ?: 1
            val limit = limitRaw?.toIntOrNull() ?: 50
            if (page < 1) return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid page"))
            if (limit !in 1..200) return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid limit"))
            val (items, total) = userAdminService.listUsers(page, limit)
            call.respond(AdminUsersPageItem(items = items, page = page, limit = limit, total = total))
        }
    }

    post("/admin/users/{id}/suspend") {
        call.withAdminAuth(authService) { adminId ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            if (id == adminId) {
                adminRouteLog.info("Admin self-suspend blocked for userId={}", adminId)
                return@withAdminAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cannot suspend your own account"))
            }
            val ok = userAdminService.suspendUser(id)
            if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
        }
    }

    delete("/admin/users/{id}/suspend") {
        call.withAdminAuth(authService) { adminId ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            if (id == adminId) {
                adminRouteLog.info("Admin self-unsuspend blocked for userId={}", adminId)
                return@withAdminAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cannot suspend your own account"))
            }
            val ok = userAdminService.unsuspendUser(id)
            if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
        }
    }

    put("/admin/users/{id}/role") {
        call.withAdminAuth(authService) { adminId ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            if (id == adminId) {
                adminRouteLog.info("Admin role self-change blocked for userId={}", adminId)
                return@withAdminAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cannot modify your own role"))
            }
            val body = runCatching { call.receive<RoleBody>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val ok = userAdminService.promoteUser(id, body.role)
            if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid role or user not found"))
        }
    }

    post("/admin/users/{id}/reset-token") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val token = passwordResetService.generateToken(id)
            call.respond(HttpStatusCode.Created, ResetTokenResponse(resetToken = token))
        }
    }

    get("/admin/settings") {
        call.withAdminAuth(authService) { _ ->
            call.respondNoStore(adminSettingsService.get())
        }
    }

    put("/admin/settings") {
        call.withAdminAuth(authService) { _ ->
            val body = runCatching { call.receive<AdminSettingsItem>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respondNoStore(adminSettingsService.upsert(body))
        }
    }
}
