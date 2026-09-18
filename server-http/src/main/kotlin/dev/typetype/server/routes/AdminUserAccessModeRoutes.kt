package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.ACCESS_MODE_ALLOW_LIST
import dev.typetype.server.services.ACCESS_MODE_UNRESTRICTED
import dev.typetype.server.services.AdminUserAccessModeResult
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.UserAdminService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
private data class AccessModeBody(val accessMode: String)

@Serializable
private data class AccessModeResponse(val accessMode: String)

fun Route.adminUserAccessModeRoutes(authService: AuthService, userAdminService: UserAdminService) {
    put("/admin/users/{id}/access-mode") {
        call.withAdminAuth(authService) { _ ->
            val id = call.parameters["id"]
                ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val body = runCatching { call.receive<AccessModeBody>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (body.accessMode !in setOf(ACCESS_MODE_UNRESTRICTED, ACCESS_MODE_ALLOW_LIST)) {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid accessMode"))
            }
            when (val result = userAdminService.setAccessMode(id, body.accessMode)) {
                is AdminUserAccessModeResult.Updated -> call.respond(AccessModeResponse(result.accessMode))
                AdminUserAccessModeResult.UserNotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }
    }
}
