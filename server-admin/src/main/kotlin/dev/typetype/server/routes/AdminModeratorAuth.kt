package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.withAdminModeratorAuth(authService: AuthService, block: suspend (userId: String) -> Unit) {
    val authHeader = request.headers["Authorization"]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing token"))
        return
    }
    val token = authHeader.substringAfter("Bearer ")
    val userId = authService.verify(token)
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
        return
    }
    val role = authService.getUserRole(userId)
    if (role != "admin" && role != "moderator") {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Admin or moderator access required"))
        return
    }
    block(userId)
}
