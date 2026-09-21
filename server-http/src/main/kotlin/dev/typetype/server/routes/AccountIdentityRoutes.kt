package dev.typetype.server.routes

import dev.typetype.server.models.AccountIdentityUpdateRequest
import dev.typetype.server.models.AdminIdentityUpdateRequest
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccountIdentityService
import dev.typetype.server.services.AccountIdentityUpdateResult
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.accountIdentityRoutes(service: AccountIdentityService, authService: AuthService) {
    get("/profile/account") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users do not have an account"))
            val identity = service.get(userId)
            if (identity == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found")) else call.respond(identity)
        }
    }
    put("/profile/account") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users do not have an account"))
            val body = runCatching { call.receive<AccountIdentityUpdateRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respondIdentityUpdate(service.updateSelf(userId, body.email, body.name, body.currentPassword))
        }
    }
}

fun Route.adminIdentityRoutes(service: AccountIdentityService, authService: AuthService) {
    put("/admin/users/{id}/identity") {
        call.withAdminAuth(authService) { _ ->
            val userId = call.parameters["id"] ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val body = runCatching { call.receive<AdminIdentityUpdateRequest>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respondIdentityUpdate(service.updateAdmin(userId, body.email, body.name))
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondIdentityUpdate(result: AccountIdentityUpdateResult): Unit = when (result) {
    AccountIdentityUpdateResult.Updated -> respond(HttpStatusCode.NoContent)
    AccountIdentityUpdateResult.InvalidInput -> respond(HttpStatusCode.BadRequest, ErrorResponse("IDENTITY_INVALID"))
    AccountIdentityUpdateResult.InvalidPassword -> respond(HttpStatusCode.Unauthorized, ErrorResponse("CURRENT_PASSWORD_INVALID"))
    AccountIdentityUpdateResult.ManagedByOidc -> respond(HttpStatusCode.Conflict, ErrorResponse("IDENTITY_PROVIDER_MANAGED"))
    AccountIdentityUpdateResult.EmailTaken -> respond(HttpStatusCode.Conflict, ErrorResponse("EMAIL_TAKEN"))
    AccountIdentityUpdateResult.UserNotFound -> respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
}
