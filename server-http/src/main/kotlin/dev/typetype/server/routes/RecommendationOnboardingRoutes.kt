package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.RecommendationOnboardingPreferencesRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RecommendationOnboardingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.recommendationOnboardingRoutes(onboardingService: RecommendationOnboardingService, authService: AuthService) {
    get("/recommendations/onboarding/topics") {
        call.withJwtAuth(authService) { call.respond(onboardingService.topics()) }
    }
    get("/recommendations/onboarding/state") {
        call.withJwtAuth(authService) { userId -> call.respond(onboardingService.state(userId)) }
    }
    put("/recommendations/onboarding/preferences") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<RecommendationOnboardingPreferencesRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(onboardingService.savePreferences(userId, request))
        }
    }
    post("/recommendations/onboarding/complete") {
        call.withJwtAuth(authService) { userId ->
            val completed = runCatching { onboardingService.complete(userId) }.getOrElse {
                return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Select at least ${dev.typetype.server.services.RecommendationOnboardingCatalog.MIN_TOPICS} topics before completing onboarding"),
                )
            }
            call.respond(completed)
        }
    }
    post("/recommendations/onboarding/skip") {
        call.withJwtAuth(authService) { userId ->
            call.respond(onboardingService.skip(userId))
        }
    }
    post("/recommendations/onboarding/reapply") {
        call.withJwtAuth(authService) { userId ->
            val reapplied = runCatching { onboardingService.reapply(userId) }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Complete onboarding before reapply"))
            }
            call.respond(reapplied)
        }
    }
}
