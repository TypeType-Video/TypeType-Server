package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.HomeRecommendationContext
import dev.typetype.server.services.HomeRecommendationCursorCodec
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.filterBlocked
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_RECOMMENDATION_SHORTS_LIMIT = 60

fun Route.homeRecommendationShortsRoutes(
    recommendationService: HomeRecommendationService,
    authService: AuthService,
    blockedService: BlockedService,
    accessControlService: AccessControlService? = null,
) {
    get("/recommendations/shorts") {
        call.withJwtAuth(authService) { userId ->
            val serviceId = call.request.queryParameters["service"]?.toIntOrNull() ?: YOUTUBE_SERVICE_ID
            if (serviceId !in VALID_SERVICE_IDS) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_RECOMMENDATION_SHORTS_LIMIT)
                ?: 20
            val debug = call.request.queryParameters["debug"]?.toBooleanStrictOrNull() ?: false
            val sessionContext = HomeRecommendationSessionContext(
                intent = HomeRecommendationSessionIntent.parse(call.request.queryParameters["intent"]),
                deviceClass = HomeRecommendationDeviceClass.parse(call.request.headers["User-Agent"]),
            )
            val rawCursor = call.request.queryParameters["cursor"]
            val cursor = HomeRecommendationCursorCodec.decode(rawCursor)
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'cursor' parameter"),
                )
            val profile = accessControlService?.profileFor(userId, authService.getUserRole(userId))
                ?: dev.typetype.server.services.AccessControlProfile.unrestricted
            val blocked = blockedService.profileFor(userId)
            val response = recommendationService.getShorts(
                    userId = userId,
                    serviceId = serviceId,
                    limit = limit,
                    cursor = cursor,
                    context = HomeRecommendationContext(serviceId, sessionContext),
                    debug = debug,
                ).filterAllowed(profile).filterBlocked(blocked)
            call.respond(response)
        }
    }
}
