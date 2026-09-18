package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SuggestionService
import dev.typetype.server.services.VALID_SERVICE_IDS
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.suggestionRoutes(
    suggestionService: SuggestionService,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/suggestions") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val query = call.request.queryParameters["query"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'query' parameter"))
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'service' parameter"))
        if (serviceId !in VALID_SERVICE_IDS)
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))

        when (val result = suggestionService.getSuggestions(query = query, serviceId = serviceId)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
