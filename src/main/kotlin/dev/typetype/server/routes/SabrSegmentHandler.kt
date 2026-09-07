package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.markServed
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import dev.typetype.server.sabr.SabrSegmentRequest

internal class SabrSegmentHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
) {
    suspend fun handle(call: ApplicationCall, isInit: Boolean, seq: Int) {
        val videoId = call.parameters["videoId"]
        val itag = call.parameters["itag"]?.toIntOrNull()
        if (videoId == null || itag == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid path"))
            return
        }
        val holder = if (call.request.queryParameters["session"] != null) {
            sabrSessionStore.lookupByToken(videoId, call.request.queryParameters["session"].orEmpty(), itag)
        } else {
            val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
            sabrSessionStore.lookupByItag(videoId, access.userId ?: videoId, itag)
        } ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
        val generation = holder.activeGeneration()
        val format = if (holder.audioFormat.itag == itag) holder.audioFormat else holder.videoFormat
        if (isInit) {
            val bytes = withTimeoutOrNull(20_000L) {
                sabrSessionStore.fetchInitializationData(holder, format)
            } ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
            return call.respondSabrMediaBytes(format.mimeType.orEmpty(), bytes)
        }
        if (seq < 1) return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        val request = SabrSegmentRequest.media(format, seq)
        sabrSessionStore.cachedSegment(holder, request)?.let { cached ->
            holder.markServed(cached)
            return call.respondSabrMediaBytes(cached.mimeType, cached.bytes)
        }
        sabrSessionStore.requestSegmentDemand(holder, request, generation)
        val segment = withTimeoutOrNull(SEGMENT_TIMEOUT_MS) {
            var fetched = sabrSessionStore.cachedSegment(holder, request)
            while (fetched == null && holder.terminalFailure() == null && holder.networkFailure() == null) {
                delay(SEGMENT_RETRY_MS)
                fetched = sabrSessionStore.cachedSegment(holder, request)
            }
            fetched
        }
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
        holder.markServed(segment)
        call.respondSabrMediaBytes(segment.mimeType, segment.bytes)
    }

}

private const val SEGMENT_TIMEOUT_MS = 60_000L
private const val SEGMENT_RETRY_MS = 500L
