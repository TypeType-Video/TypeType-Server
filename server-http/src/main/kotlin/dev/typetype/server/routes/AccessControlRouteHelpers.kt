package dev.typetype.server.routes

import dev.typetype.server.services.AccessControlProfile
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal data class AccessRouteProfile(
    val userId: String?,
    val profile: AccessControlProfile,
    val allowGuest: Boolean,
)

internal suspend fun ApplicationCall.accessProfileOrRespond(
    authService: AuthService?,
    accessControlService: AccessControlService?,
    adminSettingsService: AdminSettingsService? = null,
): AccessRouteProfile? {
    val access = publicAccessOrRespond(authService, adminSettingsService) ?: return null
    val userId = access.userId
    if (userId == null) {
        val profile = accessControlService?.profileFor(null) ?: AccessControlProfile.unrestricted
        return AccessRouteProfile(userId = null, profile = profile, allowGuest = access.allowGuest)
    }
    if (userId.startsWith("guest:")) {
        val profile = accessControlService?.profileFor(null) ?: AccessControlProfile.unrestricted
        return AccessRouteProfile(userId = userId, profile = profile, allowGuest = access.allowGuest)
    }
    val profile = accessControlService?.profileFor(userId, authService?.getUserRole(userId))
        ?: AccessControlProfile.unrestricted
    return AccessRouteProfile(userId = userId, profile = profile, allowGuest = access.allowGuest)
}

internal suspend fun ApplicationCall.requirePublicAccessOrRespond(
    authService: AuthService?,
    adminSettingsService: AdminSettingsService?,
): Boolean = publicAccessOrRespond(authService, adminSettingsService) != null

private suspend fun ApplicationCall.publicAccessOrRespond(
    authService: AuthService?,
    adminSettingsService: AdminSettingsService?,
): PublicAccess? {
    val allowGuest = adminSettingsService?.get()?.allowGuest ?: true
    if (authService == null) {
        if (allowGuest) return PublicAccess(userId = null, allowGuest = allowGuest)
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
        return null
    }
    val authHeader = request.headers["Authorization"]
    if (authHeader == null) {
        if (allowGuest) return PublicAccess(userId = null, allowGuest = allowGuest)
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
        return null
    }
    if (!authHeader.startsWith("Bearer ")) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
        return null
    }
    val userId = authService.verify(authHeader.substringAfter("Bearer "))
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
        return null
    }
    if (!allowGuest && userId.startsWith("guest:")) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
        return null
    }
    return PublicAccess(userId = userId, allowGuest = allowGuest)
}

private data class PublicAccess(val userId: String?, val allowGuest: Boolean)
