package dev.typetype.server.routes

import dev.typetype.server.models.ChannelPageRequest
import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlProfile
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedContentProfile
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.filterBlocked
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.channelRoutes(
    channelService: ChannelService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    blockedService: BlockedService? = null,
) {
    get("/channel") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val sort = call.request.queryParameters["sort"]?.takeIf { it.isNotBlank() }
        val access = call.accessProfileOrRespond(
            authService,
            accessControlService,
            adminSettingsService,
        ) ?: return@get
        val blocked = access.userId?.let { blockedService?.profileFor(it) } ?: BlockedContentProfile.empty

        when (val result = channelService.getChannel(url = url, nextpage = nextpage, sort = sort)) {
            is ExtractionResult.Success -> {
                if (!access.profile.allowsChannel(url = url, name = result.data.name)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                if (!blocked.allowsChannel(url = url, name = result.data.name)) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Channel is blocked", "content_blocked"),
                    )
                }
                call.respond(result.data.filterAllowed(access.profile).filterBlocked(blocked))
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respondExtractionFailure(result, youtube = false)
        }
    }
    post("/channel/page") {
        val request = call.receive<ChannelPageRequest>()
        val url = request.url?.takeIf { it.isNotBlank() }
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val access = call.accessProfileOrRespond(
            authService,
            accessControlService,
            adminSettingsService,
        ) ?: return@post
        val blocked = access.userId?.let { blockedService?.profileFor(it) } ?: BlockedContentProfile.empty

        call.respondChannelResult(
            channelService.getChannel(
                url = url,
                nextpage = request.nextpage?.takeIf { it.isNotBlank() },
                sort = request.sort?.takeIf { it.isNotBlank() },
            ),
            url,
            access.profile,
            blocked,
        )
    }
    get("/channel/playlists") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val access = call.accessProfileOrRespond(
            authService,
            accessControlService,
            adminSettingsService,
        ) ?: return@get
        val blocked = access.userId?.let { blockedService?.profileFor(it) } ?: BlockedContentProfile.empty
        if (!access.profile.allowsChannel(url)) {
            return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
        }

        call.respondChannelPlaylistsResult(
            channelService.getPlaylists(url = url, nextpage = nextpage),
            access.profile,
            blocked,
        )
    }
}

private suspend fun ApplicationCall.respondChannelResult(
    result: ExtractionResult<ChannelResponse>,
    url: String,
    profile: AccessControlProfile,
    blocked: BlockedContentProfile,
) {
    when (result) {
        is ExtractionResult.Success -> {
            if (!profile.allowsChannel(url = url, name = result.data.name)) {
                return respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
            }
            if (!blocked.allowsChannel(url = url, name = result.data.name)) {
                return respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is blocked", "content_blocked"))
            }
            respond(result.data.filterAllowed(profile).filterBlocked(blocked))
        }
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respondExtractionFailure(result, youtube = false)
    }
}

private suspend fun ApplicationCall.respondChannelPlaylistsResult(
    result: ExtractionResult<ChannelPlaylistsResponse>,
    profile: AccessControlProfile,
    blocked: BlockedContentProfile,
) {
    when (result) {
        is ExtractionResult.Success -> respond(result.data.filterAllowed(profile).filterBlocked(blocked))
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respondExtractionFailure(result, youtube = false)
    }
}
