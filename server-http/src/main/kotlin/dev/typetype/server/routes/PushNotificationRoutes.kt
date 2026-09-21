package dev.typetype.server.routes

import dev.typetype.server.models.ChannelNotificationPreferenceRequest
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.PushDeviceRegistrationRequest
import dev.typetype.server.services.DeviceRegistrationResult
import dev.typetype.server.services.PreferenceUpdateResult
import dev.typetype.server.services.PushNotificationService
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

internal fun Route.pushNotificationRoutes(
    service: PushNotificationService,
    authService: AuthService,
) {
    get("/notifications/channel-preferences") {
        call.withJwtAuth(authService) { userId ->
            if (!call.requirePushEnabled(service)) return@withJwtAuth
            call.respond(service.listPreferences(userId))
        }
    }
    put("/notifications/channel-preferences") {
        call.withJwtAuth(authService) { userId ->
            if (!call.requirePushEnabled(service)) return@withJwtAuth
            val request = runCatching { call.receive<ChannelNotificationPreferenceRequest>() }.getOrNull()
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid channel notification preference", "push_request_invalid"),
                )
            when (val result = service.setPreference(userId, request.channelUrl, request.enabled)) {
                is PreferenceUpdateResult.Updated -> call.respond(result.preference)
                PreferenceUpdateResult.NotSubscribed -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("Channel is not subscribed", "channel_not_subscribed"),
                )
            }
        }
    }
    get("/notifications/push/devices") {
        call.withJwtAuth(authService) { userId ->
            if (!call.requirePushEnabled(service)) return@withJwtAuth
            call.respond(service.listDevices(userId))
        }
    }
    post("/notifications/push/devices") {
        call.withJwtAuth(authService) { userId ->
            if (!call.requirePushEnabled(service)) return@withJwtAuth
            val request = runCatching { call.receive<PushDeviceRegistrationRequest>() }.getOrNull()
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid push device registration", "push_request_invalid"),
                )
            when (val result = service.registerDevice(userId, request)) {
                is DeviceRegistrationResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                is DeviceRegistrationResult.Invalid -> call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("Invalid push device registration: ${result.reason}", result.errorCode()),
                )
                DeviceRegistrationResult.UnsupportedPlatform -> call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("Push platform is not supported", "push_platform_unsupported"),
                )
                DeviceRegistrationResult.EndpointConflict -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("Push endpoint is already registered", "push_endpoint_conflict"),
                )
                DeviceRegistrationResult.LimitReached -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("Push device limit reached", "push_device_limit_reached"),
                )
            }
        }
    }
    delete("/notifications/push/devices/{deviceId}") {
        call.withJwtAuth(authService) { userId ->
            if (!call.requirePushEnabled(service)) return@withJwtAuth
            val deviceId = call.parameters["deviceId"]?.takeIf { it.isNotBlank() }
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Missing deviceId", "push_request_invalid"),
                )
            if (service.unregisterDevice(userId, deviceId)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Push device not found", "push_device_not_found"))
            }
        }
    }
}

private fun DeviceRegistrationResult.Invalid.errorCode(): String = when (reason) {
    "device_id" -> "push_device_id_invalid"
    "expires_at" -> "push_expiry_invalid"
    else -> "push_endpoint_invalid"
}

private suspend fun io.ktor.server.application.ApplicationCall.requirePushEnabled(
    service: PushNotificationService,
): Boolean {
    if (service.capability.enabled) return true
    respond(HttpStatusCode.NotFound, ErrorResponse("Push notifications are unavailable", "push_notifications_unavailable"))
    return false
}
