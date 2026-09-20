package dev.typetype.server.routes

import dev.typetype.server.models.BiliBiliQrPollRequest
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BiliBiliQrLoginResult
import dev.typetype.server.services.BiliBiliSessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.biliBiliSessionRoutes(service: BiliBiliSessionService, authService: AuthService): Unit {
    post("/bilibili-session/qr") {
        call.withJwtAuth(authService) { _ ->
            when (val result = service.startQrLogin()) {
                is BiliBiliQrLoginResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                is BiliBiliQrLoginResult.Unavailable -> call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("BiliBili Session is unavailable", "bilibili_session_unavailable"),
                )
                is BiliBiliQrLoginResult.Error -> call.respond(
                    HttpStatusCode.BadGateway,
                    ErrorResponse(result.message, "bilibili_qr_error"),
                )
            }
        }
    }
    post("/bilibili-session/qr/poll") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<BiliBiliQrPollRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (request.qrcodeKey.isBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing qrcodeKey"))
            }
            call.respond(service.pollQrLogin(userId, request.qrcodeKey))
        }
    }
    get("/bilibili-session/status") {
        call.withJwtAuth(authService) { userId ->
            call.respond(service.status(userId))
        }
    }
    delete("/bilibili-session") {
        call.withJwtAuth(authService) { userId ->
            service.delete(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
