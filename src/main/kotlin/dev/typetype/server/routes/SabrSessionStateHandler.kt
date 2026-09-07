package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal class SabrSessionStateHandler(private val sabrSessionStore: SabrSessionStore) {
    suspend fun get(call: ApplicationCall, videoId: String) {
        val holder = holderOrRespond(call, videoId) ?: return
        call.respond(stateJson(holder))
    }

    suspend fun post(call: ApplicationCall, videoId: String) {
        val holder = holderOrRespond(call, videoId) ?: return
        val body = runCatching { call.receive<JsonObject>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid SABR state body"))
        val audioActive = body.boolean("audioActive") ?: holder.isAudioActive()
        val videoActive = body.boolean("videoActive") ?: holder.isVideoActive()
        holder.setActiveTracks(videoActive = videoActive, audioActive = audioActive)
        body.long("playerTimeMs")?.let { ms ->
            holder.setPlayerTimeMs(ms)
            holder.session.streamState.setPlayerTimeMs(ms)
        }
        body.float("playbackRate")?.let { holder.session.streamState.setPlaybackRate(it) }
        val seekItag = body.int("seekItag")
        val seekSequence = body.int("seekSequence")
        if (seekItag != null && seekSequence != null && seekSequence > 0) {
            signalSeek(holder, seekItag, seekSequence)
        }
        call.respond(stateJson(holder))
    }

    private suspend fun holderOrRespond(call: ApplicationCall, videoId: String): SabrSessionHolder? {
        val token = call.request.queryParameters["session"]
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing SABR session")).let { null }
        return sabrSessionStore.lookupByToken(videoId, token)
            ?: call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request")).let { null }
    }

    private fun signalSeek(holder: SabrSessionHolder, itag: Int, sequence: Int): Unit {
        val format = formatForItag(holder, itag) ?: return
        val request = SabrSegmentRequest.media(format, sequence)
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        val startMs = holder.session.streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L)
        holder.setReaderPosition(format, startMs)
        holder.setRequestedSeekTimeMs(holder.playerTimeMs())
        if (startMs < edgeMs) {
            holder.requestRefetch(request)
        } else if (startMs > edgeMs + FORWARD_SEEK_AHEAD_MS) {
            holder.requestForwardSeek(request)
        }
    }

    private fun stateJson(holder: SabrSessionHolder): JsonObject {
        val state = holder.session.streamState
        return buildJsonObject {
            put("videoId", holder.info.videoId)
            put("session", holder.sessionToken)
            put("requestNumber", holder.session.requestNumber)
            put("complete", holder.session.isComplete)
            put("live", holder.session.isLive)
            put("cachedBytes", holder.session.cachedBytes)
            put("playerTimeMs", holder.playerTimeMs())
            put("readerHeadMs", holder.readerHeadMs())
            put("readerTailMs", holder.readerTailMs())
            put("minBufferedEndMs", state.getMinBufferedEndMs())
            put("pendingSeek", holder.hasPendingSeek())
            put("diagnosticTrace", holder.session.diagnosticTrace)
            putJsonObject("tracks") {
                putJsonObject("audio") { putTrack(holder, holder.audioFormat, holder.isAudioActive()) }
                putJsonObject("video") { putTrack(holder, holder.videoFormat, holder.isVideoActive()) }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putTrack(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        active: Boolean,
    ): Unit {
        put("itag", format.itag)
        put("active", active)
        put("maxSegment", holder.session.streamState.getMaxSegment(format))
        put("endSegment", holder.session.streamState.getEndSegment(format))
        put("bufferedEndMs", holder.session.streamState.getBufferedEndMs(format))
    }

    private fun formatForItag(holder: SabrSessionHolder, itag: Int): YoutubeSabrFormat? = when (itag) {
        holder.audioFormat.itag -> holder.audioFormat
        holder.videoFormat.itag -> holder.videoFormat
        else -> null
    }

    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

    private fun JsonObject.float(name: String): Float? = this[name]?.jsonPrimitive?.doubleOrNull?.toFloat()

    private companion object {
        const val FORWARD_SEEK_AHEAD_MS = 30_000L
    }
}
