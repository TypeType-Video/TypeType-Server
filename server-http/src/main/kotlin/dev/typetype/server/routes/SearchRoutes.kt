package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedContentProfile
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.filterBlocked
import dev.typetype.server.services.filterAllowed
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.searchRoutes(
    searchService: SearchService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    blockedService: BlockedService? = null,
) {
    get("/search/filters") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'service' parameter"))
        if (serviceId !in VALID_SERVICE_IDS)
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
        val contentFilter = call.request.queryParameters["contentFilter"]
        when (val result = searchService.filters(serviceId = serviceId, contentFilter = contentFilter)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
    get("/search") {
        val query = call.request.queryParameters["q"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'q' parameter"))
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'service' parameter"))
        if (serviceId !in VALID_SERVICE_IDS)
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val contentFilter = call.request.queryParameters["contentFilter"]
        val filters = buildList {
            addAll(call.request.queryParameters.getAll("filter").orEmpty().filter(String::isNotBlank))
            call.request.queryParameters["sortFilter"]?.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()

        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return@get
        val blocked = access.userId?.let { blockedService?.profileFor(it) } ?: BlockedContentProfile.empty
        when (val result = searchService.search(query, serviceId, nextpage, contentFilter, filters)) {
            is ExtractionResult.Success -> call.respond(result.data.filterAllowed(access.profile).filterBlocked(blocked))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
