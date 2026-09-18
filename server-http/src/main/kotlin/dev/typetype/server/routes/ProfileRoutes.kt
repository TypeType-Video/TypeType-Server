package dev.typetype.server.routes

import dev.typetype.server.models.AvatarEmojiRequest
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ProfileUpdateRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.CustomAvatarService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.ProfileUpdateResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.profileRoutes(
    profileService: ProfileService,
    avatarService: AvatarService,
    customAvatarService: CustomAvatarService,
    authService: AuthService,
) {
    put("/profile") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot update profile"))
            val req = runCatching { call.receive<ProfileUpdateRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (profileService.updateProfile(userId, req.publicUsername, req.bio)) {
                ProfileUpdateResult.Updated -> call.respond(HttpStatusCode.NoContent)
                ProfileUpdateResult.UsernameInvalidLength -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("USERNAME_INVALID_LENGTH"))
                ProfileUpdateResult.UsernameInvalidFormat -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("USERNAME_INVALID_FORMAT"))
                ProfileUpdateResult.UsernameTaken -> call.respond(HttpStatusCode.Conflict, ErrorResponse("USERNAME_TAKEN"))
                ProfileUpdateResult.BioTooLong -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("BIO_TOO_LONG"))
                ProfileUpdateResult.UserNotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }
        }
    }

    get("/profile/public/{publicUsername}") {
        val publicUsername = call.parameters["publicUsername"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing publicUsername"))
        val profile = profileService.getPublicProfile(publicUsername)
        if (profile == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found")) else call.respond(profile)
    }

    put("/profile/avatar/emoji") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot change avatar"))
            val req = runCatching { call.receive<AvatarEmojiRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val ok = profileService.setEmojiAvatar(userId, req.code, avatarService)
            if (ok) {
                customAvatarService.delete(userId)
                call.respond(HttpStatusCode.NoContent)
            } else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid OpenMoji code"))
        }
    }

    delete("/profile/avatar") {
        call.withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) return@withJwtAuth call.respond(HttpStatusCode.Forbidden, ErrorResponse("Guest users cannot change avatar"))
            val ok = profileService.clearAvatar(userId)
            if (ok) {
                customAvatarService.delete(userId)
                call.respond(HttpStatusCode.NoContent)
            } else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
        }
    }
}
