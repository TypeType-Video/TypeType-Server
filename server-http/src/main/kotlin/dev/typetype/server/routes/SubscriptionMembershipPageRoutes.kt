package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionMembershipFilter
import dev.typetype.server.services.SubscriptionMembershipPageService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.subscriptionMembershipPageRoutes(auth: AuthService, groups: SubscriptionGroupsService) {
    val service = SubscriptionMembershipPageService()
    get("/subscriptions/group-memberships/page") {
        call.withJwtAuth(auth) { userId ->
            val filter = call.membershipFilter() ?: return@withJwtAuth call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("Invalid membership page filter", "subscription_group_invalid_filter"),
            )
            val groupId = filter.groupId
            if (groupId != null && !groups.exists(userId, groupId)) {
                return@withJwtAuth call.respond(
                    HttpStatusCode.NotFound, ErrorResponse("Subscription group not found", "subscription_group_not_found"),
                )
            }
            call.respond(service.getPage(userId, filter))
        }
    }
    post("/subscriptions/group-memberships/lookup") {
        call.withJwtAuth(auth) { userId ->
            val body = call.receiveMembershipBody() ?: return@withJwtAuth
            val urls = when (val request = call.receiveMembershipChannels(body) ?: return@withJwtAuth) {
                is MembershipChannels.Single -> listOf(request.channelUrl)
                is MembershipChannels.Batch -> request.channelUrls
            }
            call.respond(service.lookup(userId, urls))
        }
    }
}

private fun ApplicationCall.membershipFilter(): SubscriptionMembershipFilter? {
    val params = request.queryParameters
    val page = params["page"]?.toIntOrNull() ?: if (params["page"] == null) 0 else return null
    val limit = params["limit"]?.toIntOrNull() ?: if (params["limit"] == null) 20 else return null
    val search = params["search"]?.trim().orEmpty()
    val group = params["groupId"]
    val ungrouped = params["ungrouped"]?.toBooleanStrictOrNull()
        ?: if (params["ungrouped"] == null) false else return null
    val excluded = params["excluded"]?.toBooleanStrictOrNull()
        ?: if (params["excluded"] == null) false else return null
    if (page !in 0..1_000_000 || limit !in 1..100 || search.length > 200) return null
    if (group != null && group.isBlank() || group != null && ungrouped || excluded && group == null) return null
    return SubscriptionMembershipFilter(page, limit, search, group, ungrouped, excluded)
}
