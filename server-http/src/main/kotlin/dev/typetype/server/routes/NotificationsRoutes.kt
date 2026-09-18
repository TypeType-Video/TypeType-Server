package dev.typetype.server.routes

import dev.typetype.server.services.AuthService
import dev.typetype.server.services.NotificationsService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private const val MAX_NOTIFICATIONS_PAGE = 10_000

fun Route.notificationsRoutes(notificationsService: NotificationsService, authService: AuthService) {
    get("/notifications/unread-count") {
        call.withJwtAuth(authService) { userId ->
            call.respond(notificationsService.getUnreadCount(userId))
        }
    }

    get("/notifications") {
        call.withJwtAuth(authService) { userId ->
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceIn(0, MAX_NOTIFICATIONS_PAGE) ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            call.respond(notificationsService.getNotifications(userId = userId, page = page, limit = limit))
        }
    }

    post("/notifications/read-all") {
        call.withJwtAuth(authService) { userId ->
            call.respond(notificationsService.markAllRead(userId))
        }
    }

    post("/notifications/{notificationId}/read") {
        call.withJwtAuth(authService) { userId ->
            val notificationId = call.parameters["notificationId"]
            if (notificationId.isNullOrBlank()) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, dev.typetype.server.models.ErrorResponse("Missing notificationId"))
                return@withJwtAuth
            }
            call.respond(notificationsService.markRead(userId, notificationId))
        }
    }
}
