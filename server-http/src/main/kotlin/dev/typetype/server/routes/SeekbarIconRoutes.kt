package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarUploadTooLargeException
import dev.typetype.server.services.CustomAvatarUploadReader
import dev.typetype.server.services.SeekbarIconSaveResult
import dev.typetype.server.services.SeekbarIconService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

private const val ICON_TOO_LARGE = "SEEKBAR_ICON_TOO_LARGE"

fun Route.seekbarIconRoutes(service: SeekbarIconService, authService: AuthService) {
    put("/profile/seekbar-icon") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) {
                return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot change the seekbar icon"))
            }
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > SeekbarIconService.MAX_ICON_BYTES) {
                return@withJwtAuth call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse(ICON_TOO_LARGE))
            }
            val bytes = try {
                CustomAvatarUploadReader.read(call.receiveChannel(), SeekbarIconService.MAX_ICON_BYTES)
            } catch (_: AvatarUploadTooLargeException) {
                return@withJwtAuth call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse(ICON_TOO_LARGE))
            }
            when (val result = service.save(userId, bytes)) {
                is SeekbarIconSaveResult.Saved -> call.respond(result.item)
                SeekbarIconSaveResult.TooLarge -> call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse(ICON_TOO_LARGE))
                SeekbarIconSaveResult.Unsupported -> call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("SEEKBAR_ICON_FORMAT_UNSUPPORTED"))
                SeekbarIconSaveResult.UserNotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }
    }
    delete("/profile/seekbar-icon") {
        call.withJwtAuth(authService) { userId ->
            service.delete(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.seekbarIconFileRoutes(service: SeekbarIconService) {
    get("/seekbar-icon/{userId}/{version}") {
        val userId = call.parameters["userId"] ?: return@get call.respondText("Icon not found", status = HttpStatusCode.NotFound)
        val version = call.parameters["version"] ?: return@get call.respondText("Icon not found", status = HttpStatusCode.NotFound)
        val icon = service.get(userId, version)
            ?: return@get call.respondText("Icon not found", status = HttpStatusCode.NotFound)
        val etag = "\"${icon.version}\""
        call.response.headers.append(HttpHeaders.ETag, etag)
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
            return@get call.respondText("", status = HttpStatusCode.NotModified)
        }
        call.respondBytes(icon.content, ContentType.parse(icon.mediaType))
    }
}
