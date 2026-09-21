package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.PresenceKeyCreateRequest
import dev.typetype.server.models.PresenceResponse
import dev.typetype.server.models.PresenceScopes
import dev.typetype.server.services.PresenceKeyService
import dev.typetype.server.services.PresenceKeyLimitException
import dev.typetype.server.services.PresenceService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.presenceRoutes(
    authService: dev.typetype.server.services.AuthService,
    presenceKeyService: PresenceKeyService,
    presenceService: PresenceService,
): Unit {
    post("/presence/keys") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) {
                return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guests cannot create presence keys"))
            }
            val body = runCatching { call.receive<PresenceKeyCreateRequest>() }.getOrNull()
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            val created = try {
                presenceKeyService.create(userId, body.name ?: "")
            } catch (error: PresenceKeyLimitException) {
                return@withJwtAuth call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(error.message ?: "Presence key limit reached", "presence_key_limit"),
                )
            } catch (error: IllegalArgumentException) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse(error.message ?: "Invalid request"))
            }
            call.respond(HttpStatusCode.Created, created)
        }
    }
    get("/presence/keys") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) {
                return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guests cannot own presence keys"))
            }
            call.respond(presenceKeyService.list(userId))
        }
    }
    delete("/presence/keys/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"].orEmpty()
            val revoked = presenceKeyService.revoke(userId, id)
            if (revoked) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, ErrorResponse("Presence key not found"))
        }
    }
    get("/presence/now-playing") {
        val principal = call.presencePrincipal(presenceKeyService) ?: return@get
        val nowPlaying = presenceService.current(principal.userId)
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.respond(
            PresenceResponse(
                active = nowPlaying != null,
                nowPlaying = nowPlaying,
                retryAfterMs = PresenceScopes.PRESENCE_RETRY_MS,
                serverTimeMs = System.currentTimeMillis(),
            ),
        )
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.presencePrincipal(
    presenceKeyService: PresenceKeyService,
): dev.typetype.server.services.PresenceTokenPrincipal? {
    val authorization = request.headers["Authorization"]
    val token = authorization?.takeIf { it.startsWith("Bearer ") }?.substringAfter("Bearer ")?.trim()
    val principal = token?.let { presenceKeyService.resolve(it) }
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid presence token"))
    }
    return principal
}
