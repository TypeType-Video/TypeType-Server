package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthenticatedSabrInfoService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.sabrRoutes(
    sabrSessionStore: SabrSessionStore,
    streamService: StreamService,
    authService: AuthService?,
    accessControlService: AccessControlService?,
    adminSettingsService: AdminSettingsService?,
    audioOnlyTokenService: AudioOnlyMediaTokenService?,
    authenticatedSabrInfoService: AuthenticatedSabrInfoService? = null,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
) {
    val sessionHandler = SabrSessionDescriptorHandler(
        sabrSessionStore,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
    )
    val stateHandler = SabrSessionStateHandler(sabrSessionStore)
    val segmentHandler = SabrSegmentHandler(
        sabrSessionStore,
        authService,
        accessControlService,
        adminSettingsService,
    )
    val manifestHandler = SabrManifestHandler(
        sabrSessionStore,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
        audioOnlyTokenService,
    )
    val playbackHandler = SabrPlaybackHandler(
        sabrSessionStore,
        streamService,
        authService,
        accessControlService,
        adminSettingsService,
        authenticatedSabrInfoService,
        youtubeSessionStreamInfo,
    )
    val playbackStateHandler = SabrPlaybackStateHandler(sabrSessionStore)
    val playbackWindowHandler = SabrPlaybackWindowHandler(sabrSessionStore)
    post("/sabr/playback/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        playbackHandler.create(call, videoId)
    }
    post("/sabr/playback/{sessionId}/seek") {
        val sessionId = call.parameters["sessionId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackHandler.seek(call, sessionId)
    }
    post("/sabr/playback/{sessionId}/window") {
        val sessionId = call.parameters["sessionId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackWindowHandler.post(call, sessionId)
    }
    post("/sabr/playback/{sessionId}/position") {
        val sessionId = call.parameters["sessionId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackWindowHandler.position(call, sessionId)
    }
    post("/sabr/playback/{sessionId}/prefetch") {
        val sessionId = call.parameters["sessionId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackWindowHandler.prefetch(call, sessionId)
    }
    post("/sabr/playback/{sessionId}/segments") {
        val sessionId = call.parameters["sessionId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackWindowHandler.segments(call, sessionId)
    }
    get("/sabr/playback/{sessionId}/manifest") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackHandler.manifest(call, sessionId)
    }
    get("/sabr/playback/{sessionId}/state") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackStateHandler.get(call, sessionId)
    }
    get("/sabr/playback/{sessionId}/{itag}/init") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        playbackHandler.segment(call, sessionId, isInit = true, seq = 0)
    }
    get("/sabr/playback/{sessionId}/{itag}/segment/{seq}") {
        val sessionId = call.parameters["sessionId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sessionId"))
        val seq = call.parameters["seq"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        playbackHandler.segment(call, sessionId, isInit = false, seq = seq)
    }
    get("/sabr/session/{videoId}/state") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        stateHandler.get(call, videoId)
    }
    post("/sabr/session/{videoId}/state") {
        val videoId = call.parameters["videoId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        stateHandler.post(call, videoId)
    }
    get("/sabr/session/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        sessionHandler.handle(call, videoId)
    }
    get("/sabr/manifest/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        manifestHandler.handle(call, videoId)
    }
    get("/sabr/download/{videoId}") {
        val videoId = call.parameters["videoId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        manifestHandler.handle(call, videoId, download = true)
    }
    get("/sabr/{videoId}/{itag}/init") {
        segmentHandler.handle(call, isInit = true, seq = 0)
    }
    get("/sabr/{videoId}/{itag}/segment/{seq}") {
        val seq = call.parameters["seq"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        segmentHandler.handle(call, isInit = false, seq = seq)
    }
}
