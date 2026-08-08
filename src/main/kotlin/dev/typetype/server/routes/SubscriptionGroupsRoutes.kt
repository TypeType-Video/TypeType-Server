package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionGroupMembershipRequest
import dev.typetype.server.models.SubscriptionGroupRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.subscriptionGroupsRoutes(groupsService: SubscriptionGroupsService, authService: AuthService) {
    get("/subscriptions/groups") {
        call.withJwtAuth(authService) { userId -> call.respond(groupsService.getAll(userId)) }
    }
    post("/subscriptions/groups") {
        call.withJwtAuth(authService) { userId ->
            val request = call.receiveGroupRequest() ?: return@withJwtAuth
            call.respondGroupWrite(groupsService.create(userId, request.name), created = true)
        }
    }
    put("/subscriptions/groups/{groupId}") {
        call.withJwtAuth(authService) { userId ->
            val groupId = call.groupId() ?: return@withJwtAuth call.respondMissingGroupId()
            val request = call.receiveGroupRequest() ?: return@withJwtAuth
            call.respondGroupWrite(groupsService.rename(userId, groupId, request.name), created = false)
        }
    }
    delete("/subscriptions/groups/{groupId}") {
        call.withJwtAuth(authService) { userId ->
            val groupId = call.groupId() ?: return@withJwtAuth call.respondMissingGroupId()
            if (groupsService.delete(userId, groupId)) call.respond(HttpStatusCode.NoContent) else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Subscription group not found", "subscription_group_not_found"))
            }
        }
    }
    put("/subscriptions/groups/{groupId}/channels") {
        call.withJwtAuth(authService) { userId ->
            val groupId = call.groupId() ?: return@withJwtAuth call.respondMissingGroupId()
            val request = runCatching { call.receive<SubscriptionGroupMembershipRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (request.channelUrl.isBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("channelUrl must not be blank"))
            }
            call.respondMembership(groupsService.addSubscription(userId, groupId, request.channelUrl))
        }
    }
    delete("/subscriptions/groups/{groupId}/channels") {
        call.withJwtAuth(authService) { userId ->
            val groupId = call.groupId() ?: return@withJwtAuth call.respondMissingGroupId()
            val channelUrl = call.request.queryParameters["url"]?.takeIf(String::isNotBlank)
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            call.respondMembership(groupsService.removeSubscription(userId, groupId, channelUrl))
        }
    }
}

private fun ApplicationCall.groupId(): String? = parameters["groupId"]?.takeIf(String::isNotBlank)

private suspend fun ApplicationCall.receiveGroupRequest(): SubscriptionGroupRequest? =
    runCatching { receive<SubscriptionGroupRequest>() }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        null
    }

private suspend fun ApplicationCall.respondGroupWrite(result: SubscriptionGroupWriteResult, created: Boolean) {
    when (result) {
        is SubscriptionGroupWriteResult.Success -> if (created) respond(HttpStatusCode.Created, result.group) else {
            respond(HttpStatusCode.NoContent)
        }
        SubscriptionGroupWriteResult.InvalidName -> respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Group name must contain 1 to 100 characters", "subscription_group_invalid_name"),
        )
        SubscriptionGroupWriteResult.DuplicateName -> respond(
            HttpStatusCode.Conflict,
            ErrorResponse("A subscription group with this name already exists", "subscription_group_name_conflict"),
        )
        SubscriptionGroupWriteResult.NotFound -> respond(
            HttpStatusCode.NotFound,
            ErrorResponse("Subscription group not found", "subscription_group_not_found"),
        )
    }
}

private suspend fun ApplicationCall.respondMembership(result: SubscriptionGroupMembershipResult) {
    when (result) {
        SubscriptionGroupMembershipResult.Success -> respond(HttpStatusCode.NoContent)
        SubscriptionGroupMembershipResult.GroupNotFound -> respond(
            HttpStatusCode.NotFound,
            ErrorResponse("Subscription group not found", "subscription_group_not_found"),
        )
        SubscriptionGroupMembershipResult.SubscriptionNotFound -> respond(
            HttpStatusCode.NotFound,
            ErrorResponse("Subscription not found", "subscription_not_found"),
        )
        SubscriptionGroupMembershipResult.MembershipNotFound -> respond(
            HttpStatusCode.NotFound,
            ErrorResponse("Subscription group membership not found", "subscription_group_membership_not_found"),
        )
    }
}

private suspend fun ApplicationCall.respondMissingGroupId() =
    respond(HttpStatusCode.BadRequest, ErrorResponse("Missing groupId"))
