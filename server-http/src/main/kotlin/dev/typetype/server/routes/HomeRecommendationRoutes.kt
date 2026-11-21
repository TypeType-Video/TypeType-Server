package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.HomeRecommendationCursorCodec
import dev.typetype.server.services.HomeRecommendationContext
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.RecommenderHomeService
import dev.typetype.server.services.RecommenderPageCursor
import dev.typetype.server.services.startsLater
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.filterBlocked
import dev.typetype.server.services.filterFocus
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_RECOMMENDATION_LIMIT = 60

fun Route.homeRecommendationRoutes(
    recommendationService: HomeRecommendationService,
    authService: AuthService,
    blockedService: BlockedService,
    accessControlService: AccessControlService? = null,
    recommenderHomeService: RecommenderHomeService? = null,
    focusService: FocusService? = null,
) {
    get("/recommendations/home") {
        call.withJwtAuth(authService) { userId ->
            val serviceId = call.request.queryParameters["service"]?.toIntOrNull() ?: YOUTUBE_SERVICE_ID
            if (serviceId !in VALID_SERVICE_IDS) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_RECOMMENDATION_LIMIT)
                ?: 20
            val sessionContext = HomeRecommendationSessionContext(
                intent = HomeRecommendationSessionIntent.parse(call.request.queryParameters["intent"]),
                deviceClass = HomeRecommendationDeviceClass.parse(call.request.headers["User-Agent"]),
            )
            val rawCursor = call.request.queryParameters["cursor"]
            val profile = accessControlService?.profileFor(userId, authService.getUserRole(userId))
                ?: dev.typetype.server.services.AccessControlProfile.unrestricted
            val blocked = blockedService.profileFor(userId)
            val focus = focusService?.profileFor(userId)
            val fromRecommender = recommenderHomeService
                ?.takeIf { serviceId == YOUTUBE_SERVICE_ID }
                ?.getHome(userId, limit, rawCursor, call.recommenderLocale(), call.recommenderTimezone())
            if (fromRecommender != null) {
                return@withJwtAuth call.respond(fromRecommender.filterAllowed(profile).filterBlocked(blocked).filterFocus(focus).withShownRows())
            }
            val preparing = recommenderHomeService?.isPreparing(userId)?.takeIf { it }
            val cursor = HomeRecommendationCursorCodec.decode(rawCursor.takeUnless(RecommenderPageCursor::isRecommenderCursor))
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'cursor' parameter"),
                )
            val response = recommendationService.getHome(
                    userId = userId,
                    serviceId = serviceId,
                    limit = limit,
                    cursor = cursor,
                    context = HomeRecommendationContext(serviceId, sessionContext),
                ).filterAllowed(profile).filterBlocked(blocked).filterFocus(focus)
            val now = System.currentTimeMillis()
            call.respond(response.copy(items = response.items.filterNot { it.startsLater(now) }, preparing = preparing))
        }
    }
}
