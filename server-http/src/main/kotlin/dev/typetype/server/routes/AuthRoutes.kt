package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.UserProfileItem
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.HomeRecommendationWarmup
import dev.typetype.server.services.NoopHomeRecommendationWarmup
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.authRoutes(
    authService: AuthService,
    passwordResetService: PasswordResetService,
    profileService: ProfileService,
    adminSettingsService: AdminSettingsService,
    warmupService: HomeRecommendationWarmup = NoopHomeRecommendationWarmup,
    sessionConfig: AuthSessionConfig = AuthSessionConfig(),
) {
    registerRoutes(authService, adminSettingsService, warmupService, sessionConfig)

    post("/auth/login") {
        if (!adminSettingsService.get().localLoginEnabled) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Local login is disabled"))
            return@post
        }
        val req = call.receive<LoginRequest>()
        val identifier = req.identifier?.trim().orEmpty().ifBlank { req.email?.trim().orEmpty() }
        val token = authService.login(identifier, req.password)
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
            return@post
        }
        AuthCookieHelpers.setRefreshCookie(call.response, token.refreshToken, sessionConfig)
        token.accessToken.warm(authService, warmupService)
        call.respond(SessionResponse(token.accessToken))
    }
    post("/auth/refresh") {
        val req = runCatching { call.receive<RefreshRequest>() }.getOrNull()
        val refreshToken = AuthCookieHelpers.extractRefreshToken(call) ?: req?.token
        val newToken = refreshToken?.let { authService.refreshSession(it) }
        if (newToken == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
            return@post
        }
        AuthCookieHelpers.setRefreshCookie(call.response, newToken.refreshToken, sessionConfig)
        newToken.accessToken.warm(authService, warmupService)
        call.respond(SessionResponse(newToken.accessToken))
    }
    post("/auth/logout") {
        val refreshToken = AuthCookieHelpers.extractRefreshToken(call)
        authService.logout(refreshToken)
        AuthCookieHelpers.clearRefreshCookie(call.response, sessionConfig)
        call.respond(HttpStatusCode.NoContent)
    }

    get("/auth/me") {
        val authHeader = call.request.headers["Authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing token"))
            return@get
        }
        val token = authHeader.substringAfter("Bearer ")
        val userId = authService.verify(token)
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
            return@get
        }
        val role = authService.getUserRole(userId)
        val profile = if (userId.startsWith("guest:")) null else profileService.getProfile(userId)
        call.respond(
            UserProfileItem(
                id = userId,
                role = role,
                publicUsername = profile?.publicUsername,
                bio = profile?.bio,
                avatarUrl = profile?.avatarUrl,
                avatarType = profile?.avatarType,
                avatarCode = profile?.avatarCode,
            )
        )
    }

    post("/auth/guest") {
        if (!adminSettingsService.get().allowGuest) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Guest access is disabled"))
            return@post
        }
        val token = authService.guestLogin()
        token.warm(authService, warmupService)
        call.respond(AuthResponse(token))
    }

    post("/auth/reset-password") {
        val req = runCatching { call.receive<ResetPasswordRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            return@post
        }
        if (req.newPassword.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password cannot be blank"))
            return@post
        }
        val ok = passwordResetService.resetPassword(req.resetToken, req.newPassword)
        if (ok) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid or expired reset token"))
    }
}

private suspend fun String.warm(authService: AuthService, warmupService: HomeRecommendationWarmup): Unit =
    authService.verify(this)?.let(warmupService::markActive) ?: Unit
