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
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.filterAllowed
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
) {
    get("/channel") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val sort = call.request.queryParameters["sort"]?.takeIf { it.isNotBlank() }
        val profile = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)?.profile ?: return@get

        when (val result = channelService.getChannel(url = url, nextpage = nextpage, sort = sort)) {
            is ExtractionResult.Success -> {
                if (!profile.allowsChannel(url = url, name = result.data.name)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                call.respond(result.data.filterAllowed(profile))
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
    post("/channel/page") {
        val request = call.receive<ChannelPageRequest>()
        val url = request.url?.takeIf { it.isNotBlank() }
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val profile = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)?.profile ?: return@post

        call.respondChannelResult(
            channelService.getChannel(
                url = url,
                nextpage = request.nextpage?.takeIf { it.isNotBlank() },
                sort = request.sort?.takeIf { it.isNotBlank() },
            ),
            url,
            profile,
        )
    }
    get("/channel/playlists") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val profile = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)?.profile ?: return@get
        if (!profile.allowsChannel(url)) {
            return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
        }

        call.respondChannelPlaylistsResult(channelService.getPlaylists(url = url, nextpage = nextpage), profile)
    }
}

private suspend fun ApplicationCall.respondChannelResult(
    result: ExtractionResult<ChannelResponse>,
    url: String,
    profile: AccessControlProfile,
) {
    when (result) {
        is ExtractionResult.Success -> {
            if (!profile.allowsChannel(url = url, name = result.data.name)) {
                return respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
            }
            respond(result.data.filterAllowed(profile))
        }
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
    }
}

private suspend fun ApplicationCall.respondChannelPlaylistsResult(
    result: ExtractionResult<ChannelPlaylistsResponse>,
    profile: AccessControlProfile,
) {
    when (result) {
        is ExtractionResult.Success -> respond(result.data.filterAllowed(profile))
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
    }
}
