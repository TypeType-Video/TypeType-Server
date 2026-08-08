package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HomeRecommendationWarmup
import dev.typetype.server.services.NoopHomeRecommendationWarmup
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionSelection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun Route.subscriptionsRoutes(
    subscriptionsService: SubscriptionsService,
    authService: AuthService,
    warmupService: HomeRecommendationWarmup = NoopHomeRecommendationWarmup,
    groupsService: SubscriptionGroupsService = SubscriptionGroupsService(),
) {
    get("/subscriptions") {
        call.withJwtAuth(authService) { userId ->
            val parsed = call.parseSubscriptionSelection()
            if (parsed !is SubscriptionSelectionParseResult.Valid) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid subscription filter"))
            }
            val selection = parsed.selection
            if (selection is SubscriptionSelection.Group && !groupsService.exists(userId, selection.id)) {
                return@withJwtAuth call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Subscription group not found", "subscription_group_not_found"),
                )
            }
            call.respond(subscriptionsService.getAll(userId, selection))
        }
    }
    post("/subscriptions") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<SubscriptionItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val subscription = subscriptionsService.add(userId, item)
            warmupService.invalidateAndWarm(userId)
            call.respond(HttpStatusCode.Created, subscription)
        }
    }
    delete("/subscriptions") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.request.queryParameters["url"]?.takeIf { it.isNotBlank() }
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            call.respondDeleteResult(subscriptionsService, warmupService, userId, channelUrl)
        }
    }
    delete("/subscriptions/{channelUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.extractDeleteChannelUrl()
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            call.respondDeleteResult(subscriptionsService, warmupService, userId, channelUrl)
        }
    }
}

private suspend fun ApplicationCall.respondDeleteResult(
    subscriptionsService: SubscriptionsService,
    warmupService: HomeRecommendationWarmup,
    userId: String,
    channelUrl: String,
) {
    val deleted = subscriptionsService.delete(userId, channelUrl)
    if (deleted) warmupService.invalidateAndWarm(userId)
    if (deleted) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
}

private fun ApplicationCall.extractDeleteChannelUrl(): String? {
    val queryUrl = request.queryParameters["url"]?.takeIf { it.isNotBlank() }
    if (queryUrl != null) return queryUrl
    val rawPath = request.path()
    val marker = "/subscriptions/"
    val index = rawPath.indexOf(marker)
    if (index == -1) return null
    val rawTail = rawPath.substring(index + marker.length)
    if (rawTail.isBlank()) return null
    return runCatching { URLDecoder.decode(rawTail, StandardCharsets.UTF_8) }
        .getOrDefault(rawTail)
        .restoreCollapsedScheme()
        .takeIf { it.isNotBlank() }
}

private fun String.restoreCollapsedScheme(): String {
    val match = Regex("^([A-Za-z][A-Za-z0-9+.-]*):/([^/].*)$").matchEntire(this) ?: return this
    return "${match.groupValues[1]}://${match.groupValues[2]}"
}
