package dev.typetype.server.routes

import dev.typetype.server.models.DeviceStartRequest
import dev.typetype.server.models.DeviceTokenRequest
import dev.typetype.server.models.DeviceUserCodeRequest
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.HomeRecommendationWarmup
import dev.typetype.server.services.NoopHomeRecommendationWarmup
import dev.typetype.server.services.device.DeviceAuthorizationService
import dev.typetype.server.services.device.DeviceDecisionResult
import dev.typetype.server.services.device.DevicePollResult
import dev.typetype.server.services.device.DeviceStartResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.deviceAuthRoutes(
    deviceAuthorization: DeviceAuthorizationService,
    authService: AuthService,
    sessionConfig: AuthSessionConfig = AuthSessionConfig(),
    warmupService: HomeRecommendationWarmup = NoopHomeRecommendationWarmup,
) {
    post("/auth/device/start") {
        call.noStore()
        val request = runCatching { call.receive<DeviceStartRequest>() }.getOrDefault(DeviceStartRequest())
        when (val result = deviceAuthorization.start(request.deviceName, call.clientKey())) {
            is DeviceStartResult.Started -> call.respond(result.response)
            DeviceStartResult.Disabled -> call.respondError(HttpStatusCode.Forbidden, DISABLED)
            DeviceStartResult.RateLimited -> call.respondError(HttpStatusCode.TooManyRequests, "rate_limited")
        }
    }
    get("/auth/device/lookup") {
        call.noStore()
        call.withJwtAuth(authService) { userId ->
            val code = call.request.queryParameters["code"].orEmpty()
            call.respondDecision(deviceAuthorization.lookup(userId, code, call.clientKey()))
        }
    }
    post("/auth/device/approve") {
        call.noStore()
        call.withJwtAuth(authService) { userId ->
            val request = call.userCodeRequest() ?: return@withJwtAuth call.respondError(HttpStatusCode.BadRequest, INVALID)
            call.respondDecision(deviceAuthorization.approve(userId, request.userCode, call.clientKey()))
        }
    }
    post("/auth/device/deny") {
        call.noStore()
        call.withJwtAuth(authService) { userId ->
            val request = call.userCodeRequest() ?: return@withJwtAuth call.respondError(HttpStatusCode.BadRequest, INVALID)
            call.respondDecision(deviceAuthorization.deny(userId, request.userCode, call.clientKey()))
        }
    }
    post("/auth/device/token") {
        call.noStore()
        val request = runCatching { call.receive<DeviceTokenRequest>() }.getOrNull()
            ?: return@post call.respondError(HttpStatusCode.BadRequest, INVALID)
        when (val result = deviceAuthorization.poll(request.deviceCode)) {
            is DevicePollResult.Approved -> {
                val tokens = authService.issueSessionForOwner(result.ownerUserId)
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "access_denied")
                AuthCookieHelpers.setRefreshCookie(call.response, tokens.refreshToken, sessionConfig)
                authService.verify(tokens.accessToken)?.let(warmupService::markActive)
                call.respond(SessionResponse(tokens.accessToken))
            }
            is DevicePollResult.Pending ->
                call.respondError(HttpStatusCode.BadRequest, if (result.scanned) "authorization_scanned" else "authorization_pending")
            DevicePollResult.SlowDown -> call.respondError(HttpStatusCode.BadRequest, "slow_down")
            DevicePollResult.Expired -> call.respondError(HttpStatusCode.BadRequest, "expired_token")
            DevicePollResult.Denied -> call.respondError(HttpStatusCode.BadRequest, "access_denied")
            DevicePollResult.Disabled -> call.respondError(HttpStatusCode.Forbidden, DISABLED)
            DevicePollResult.Invalid -> call.respondError(HttpStatusCode.BadRequest, INVALID)
        }
    }
}

private suspend fun ApplicationCall.respondDecision(result: DeviceDecisionResult) = when (result) {
    is DeviceDecisionResult.Found -> respond(result.request)
    DeviceDecisionResult.Approved, DeviceDecisionResult.Denied -> respond(HttpStatusCode.NoContent)
    DeviceDecisionResult.Disabled -> respondError(HttpStatusCode.Forbidden, DISABLED)
    DeviceDecisionResult.GuestForbidden -> respondError(HttpStatusCode.Forbidden, "guest_forbidden")
    DeviceDecisionResult.Locked -> respondError(HttpStatusCode.TooManyRequests, "too_many_attempts")
    DeviceDecisionResult.NotFound -> respondError(HttpStatusCode.NotFound, "code_not_found")
    DeviceDecisionResult.AlreadyDecided -> respondError(HttpStatusCode.Conflict, "already_decided")
}

private suspend fun ApplicationCall.userCodeRequest(): DeviceUserCodeRequest? =
    runCatching { receive<DeviceUserCodeRequest>() }.getOrNull()

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String) =
    respond(status, ErrorResponse(code.replace('_', ' '), code = code))

private fun ApplicationCall.noStore() = response.header(HttpHeaders.CacheControl, "no-store")

private fun ApplicationCall.clientKey(): String = request.headers["X-Real-IP"] ?: request.local.remoteHost

private const val DISABLED = "device_login_disabled"
private const val INVALID = "invalid_request"
