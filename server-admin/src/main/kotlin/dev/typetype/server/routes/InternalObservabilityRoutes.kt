package dev.typetype.server.routes

import dev.typetype.server.AppMetrics
import dev.typetype.server.models.DeepHealthResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.internalObservabilityRoutes(
    healthProvider: suspend () -> DeepHealthResponse,
    metricsProvider: () -> String = AppMetrics::snapshot,
    tokenProvider: () -> String? = { System.getenv("INTERNAL_OBSERVABILITY_TOKEN") },
) {
    route("/internal") {
        get("/health/deep") {
            if (!call.isInternalAuthorized(tokenProvider)) return@get call.respond(HttpStatusCode.NotFound)
            call.respond(healthProvider())
        }
        get("/metrics") {
            if (!call.isInternalAuthorized(tokenProvider)) return@get call.respond(HttpStatusCode.NotFound)
            call.respondText(metricsProvider(), ContentType.Text.Plain)
        }
    }
}

private fun ApplicationCall.isInternalAuthorized(tokenProvider: () -> String?): Boolean {
    val expected = tokenProvider()?.takeIf { it.isNotBlank() } ?: return false
    val provided = request.headers["X-Internal-Token"] ?: request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
    return provided == expected
}
