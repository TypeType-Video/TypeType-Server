package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ProfileNameRequest
import dev.typetype.server.models.ProfileSwitchResponse
import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.ProfileAccountService
import dev.typetype.server.services.ProfileMutationResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.accountProfilesRoutes(
    profileService: ProfileAccountService,
    authService: AuthService,
    sessionConfig: AuthSessionConfig,
) {
    get("/profiles") {
        call.withJwtAuth(authService) { userId ->
            val profiles = profileService.list(userId)
            if (profiles == null) call.respond(HttpStatusCode.Forbidden, ErrorResponse("Profiles are unavailable"))
            else call.respond(profiles)
        }
    }

    post("/profiles") {
        call.withJwtAuth(authService) { userId ->
            val body = call.receiveNameOrNull() ?: return@withJwtAuth
            when (val result = profileService.create(userId, body.name)) {
                is ProfileMutationResult.Success -> call.respond(HttpStatusCode.Created, result.profile)
                ProfileMutationResult.InvalidName -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("PROFILE_NAME_INVALID"))
                ProfileMutationResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
                else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Profile operation failed"))
            }
        }
    }

    put("/profiles/{profileId}") {
        call.withJwtAuth(authService) { userId ->
            val profileId = call.parameters["profileId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing profileId"))
            val body = call.receiveNameOrNull() ?: return@withJwtAuth
            call.respondMutation(profileService.rename(userId, profileId, body.name))
        }
    }

    post("/profiles/{profileId}/default") {
        call.withJwtAuth(authService) { userId ->
            val profileId = call.parameters["profileId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing profileId"))
            call.respondMutation(profileService.setDefault(userId, profileId))
        }
    }

    post("/profiles/{profileId}/switch") {
        call.withJwtAuth(authService) { userId ->
            val profileId = call.parameters["profileId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing profileId"))
            val profile = profileService.switch(userId, profileId)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
                return@withJwtAuth
            }
            val token = authService.issueSession(profile.id)
            if (token == null) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create session"))
                return@withJwtAuth
            }
            AuthCookieHelpers.setRefreshCookie(call.response, token.refreshToken, sessionConfig)
            call.respond(ProfileSwitchResponse(token.accessToken, profile))
        }
    }

    delete("/profiles/{profileId}") {
        call.withJwtAuth(authService) { userId ->
            val profileId = call.parameters["profileId"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing profileId"))
            when (profileService.delete(userId, profileId)) {
                ProfileMutationResult.Deleted -> call.respond(HttpStatusCode.NoContent)
                ProfileMutationResult.CannotDeleteOwner -> call.respond(HttpStatusCode.Conflict, ErrorResponse("The account profile cannot be deleted"))
                ProfileMutationResult.CannotDeleteActive -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Switch profiles before deleting the active profile"))
                ProfileMutationResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
                else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Profile deletion failed"))
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.receiveNameOrNull(): ProfileNameRequest? =
    runCatching { receive<ProfileNameRequest>() }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        null
    }

private suspend fun io.ktor.server.application.ApplicationCall.respondMutation(result: ProfileMutationResult): Unit = when (result) {
    is ProfileMutationResult.Success -> respond(result.profile)
    ProfileMutationResult.InvalidName -> respond(HttpStatusCode.BadRequest, ErrorResponse("PROFILE_NAME_INVALID"))
    ProfileMutationResult.NotFound -> respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
    else -> respond(HttpStatusCode.BadRequest, ErrorResponse("Profile operation failed"))
}
