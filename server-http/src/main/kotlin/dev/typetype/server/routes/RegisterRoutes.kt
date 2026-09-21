package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.HomeRecommendationWarmup
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.registerRoutes(
    authService: AuthService,
    adminSettingsService: AdminSettingsService,
    warmupService: HomeRecommendationWarmup,
    sessionConfig: AuthSessionConfig,
): Unit {
    get("/auth/register/status") {
        val bootstrapAvailable = !authService.hasAdmin()
        val settings = adminSettingsService.get()
        call.respondNoStore(
            RegisterStatusResponse(
                allowRegistration = settings.allowRegistration,
                bootstrapAvailable = bootstrapAvailable,
                localLoginEnabled = settings.localLoginEnabled,
            )
        )
    }

    post("/auth/register") {
        val req = call.receive<RegisterRequest>()
        if (req.email.isBlank() || req.password.isBlank() || req.name.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing fields"))
            return@post
        }
        val settings = adminSettingsService.get()
        if (!settings.localLoginEnabled) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Local registration is disabled"))
            return@post
        }
        val bootstrapAvailable = !authService.hasAdmin()
        val registrationAllowed = settings.allowRegistration || bootstrapAvailable
        if (!registrationAllowed) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Registration is disabled"))
            return@post
        }
        try {
            val token = authService.register(req.email, req.password, req.name)
            authService.verify(token.accessToken)?.let(warmupService::markActive)
            AuthCookieHelpers.setRefreshCookie(call.response, token.refreshToken, sessionConfig)
            call.respond(SessionResponse(token.accessToken))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Registration failed"))
        }
    }
}
