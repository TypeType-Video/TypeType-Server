package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarUploadTooLargeException
import dev.typetype.server.services.CustomAvatarSaveResult
import dev.typetype.server.services.CustomAvatarService
import dev.typetype.server.services.CustomAvatarUploadReader
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put

fun Route.customAvatarRoutes(service: CustomAvatarService, authService: AuthService) {
    put("/profile/avatar/custom") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot change avatar"))
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > CustomAvatarService.MAX_AVATAR_BYTES) {
                return@withJwtAuth call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("AVATAR_TOO_LARGE"))
            }
            val bytes = try {
                CustomAvatarUploadReader.read(call.receiveChannel(), CustomAvatarService.MAX_AVATAR_BYTES)
            } catch (_: AvatarUploadTooLargeException) {
                return@withJwtAuth call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("AVATAR_TOO_LARGE"))
            }
            when (val result = service.save(userId, bytes)) {
                is CustomAvatarSaveResult.Saved -> call.respond(result.item)
                CustomAvatarSaveResult.TooLarge -> call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("AVATAR_TOO_LARGE"))
                CustomAvatarSaveResult.Unsupported -> call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("AVATAR_FORMAT_UNSUPPORTED"))
                CustomAvatarSaveResult.UserNotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }
    }
}
