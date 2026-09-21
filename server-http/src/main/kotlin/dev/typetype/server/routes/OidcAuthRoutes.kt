package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.OidcCallbackRequest
import dev.typetype.server.models.OidcCallbackResponse
import dev.typetype.server.models.OidcStartResponse
import dev.typetype.server.models.OidcStatusResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.OidcAuthService
import dev.typetype.server.services.OidcCallbackSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.oidcAuthRoutes(
    oidcAuthService: OidcAuthService,
    adminSettingsService: AdminSettingsService,
    sessionConfig: AuthSessionConfig = AuthSessionConfig(),
) {
    get("/auth/oidc/status") {
        val settings = adminSettingsService.get()
        val config = oidcAuthService.publicConfig()
        call.respond(
            OidcStatusResponse(
                enabled = config.enabled,
                providerName = config.providerName,
                localLoginEnabled = settings.localLoginEnabled,
                autoRedirect = config.enabled && settings.oidcAutoRedirect,
            )
        )
    }
    get("/auth/oidc/start") {
        val redirectUri = call.request.queryParameters["redirectUri"]
        val returnTo = call.request.queryParameters["returnTo"]
        call.respondOidcStartResult(oidcAuthService.start(redirectUri = redirectUri, returnTo = returnTo))
    }
    post("/auth/oidc/callback") {
        val request = runCatching { call.receive<OidcCallbackRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        }
        call.respondOidcCallbackResult(oidcAuthService.callback(request), sessionConfig)
    }
}

private suspend fun ApplicationCall.respondOidcStartResult(result: ExtractionResult<OidcStartResponse>) {
    when (result) {
        is ExtractionResult.Success -> respond(result.data)
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
    }
}

private suspend fun ApplicationCall.respondOidcCallbackResult(
    result: ExtractionResult<OidcCallbackSession>,
    sessionConfig: AuthSessionConfig,
) {
    when (result) {
        is ExtractionResult.Success -> {
            AuthCookieHelpers.setRefreshCookie(response, result.data.refreshToken, sessionConfig)
            respond(OidcCallbackResponse(accessToken = result.data.accessToken, returnTo = result.data.returnTo))
        }
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
    }
}
