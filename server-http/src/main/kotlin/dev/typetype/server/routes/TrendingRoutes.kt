package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.filterAllowed
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.trendingRoutes(
    trendingService: TrendingService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/trending") {
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'service' parameter"))
        if (serviceId !in VALID_SERVICE_IDS)
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))

        val profile = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)?.profile ?: return@get
        when (val result = trendingService.getTrending(serviceId = serviceId)) {
            is ExtractionResult.Success -> call.respond(result.data.filterAllowed(profile))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
