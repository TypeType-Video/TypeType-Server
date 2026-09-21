package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HomeRecommendationOfflineEvaluator
import dev.typetype.server.services.HomeRecommendationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.homeRecommendationMetricsRoutes(
    recommendationService: HomeRecommendationService,
    authService: AuthService,
) {
    get("/recommendations/home/metrics") {
        call.withJwtAuth(authService) { userId ->
            val serviceId = call.request.queryParameters["service"]?.toIntOrNull() ?: 0
            val clicked = call.request.queryParameters["clicked"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            if (clicked.isEmpty()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing clicked urls"))
            }
            val response = recommendationService.getHome(
                userId = userId,
                serviceId = serviceId,
                limit = 10,
                cursor = dev.typetype.server.services.HomeRecommendationCursor(),
                context = dev.typetype.server.services.HomeRecommendationContext(
                    serviceId = serviceId,
                    sessionContext = dev.typetype.server.services.HomeRecommendationSessionContext(
                        intent = dev.typetype.server.services.HomeRecommendationSessionIntent.AUTO,
                        deviceClass = dev.typetype.server.services.HomeRecommendationDeviceClass.UNKNOWN,
                    ),
                ),
            )
            call.respond(HomeRecommendationOfflineEvaluator.evaluate(response.items, clicked))
        }
    }
}
