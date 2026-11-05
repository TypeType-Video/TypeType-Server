package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedContentProfile
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelPostsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.channelPostsRoutes(
    postsService: ChannelPostsService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    blockedService: BlockedService? = null,
) {
    get("/channel/posts") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]?.takeIf { it.isNotBlank() }
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return@get
        val blocked = access.userId?.let { blockedService?.profileFor(it) } ?: BlockedContentProfile.empty

        when (val result = postsService.getPosts(url = url, nextpage = nextpage)) {
            is ExtractionResult.Success -> {
                val name = result.data.channelName
                if (!access.profile.allowsChannel(url = url, name = name.ifBlank { null })) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                if (!blocked.allowsChannel(url = url, name = name)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is blocked", "content_blocked"))
                }
                call.respond(result.data)
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, youtube = false)
        }
    }
}
