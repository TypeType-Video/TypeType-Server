package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import dev.typetype.server.sabr.YoutubeSabrFormat
import kotlin.math.max

internal class SabrSessionDescriptorHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val streamService: StreamService,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
) {
    suspend fun handle(call: ApplicationCall, videoId: String) {
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)
            ?: return
        val url = "https://www.youtube.com/watch?v=$videoId"
        if (access.profile.enabled) {
            when (val result = streamService.getStreamInfo(url)) {
                is ExtractionResult.Success -> {
                    if (!access.profile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                        return call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                    }
                }
                is ExtractionResult.Failure -> return call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse(result.message),
                )
                is ExtractionResult.BadRequest -> return call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(result.message),
                )
            }
        }
        val startTimeMs = call.request.queryParameters["playerTimeMs"]?.toLongOrNull()?.coerceAtLeast(0L)
            ?: call.request.queryParameters["startTimeMs"]?.toLongOrNull()?.coerceAtLeast(0L)
            ?: 0L
        val prepared = sabrSessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = true)
            ?: return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
        val audio = SabrFormatSelector.audio(
            prepared.info,
            call.request.queryParameters["audioItag"]?.toIntOrNull(),
            call.request.queryParameters["audioTrackId"],
            requireAac = true,
        ) ?: return call.respond(
            HttpStatusCode.UnprocessableEntity,
            ErrorResponse("No SABR audio for this video"),
        )
        val video = SabrFormatSelector.video(
            prepared.info,
            call.request.queryParameters["videoItag"]?.toIntOrNull(),
        )
            ?: return call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("No SABR video for this video"),
            )
        val userId = access.userId ?: videoId
        val holder = createHolder(videoId, userId, prepared, audio, video, startTimeMs)
        val readyHolder = preflightOrRecreate(videoId, userId, prepared, audio, video, startTimeMs, holder)
        if (readyHolder == null) {
            return call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR preflight failed"))
        }
        sabrSessionStore.startPump(readyHolder)
        call.respond(buildJsonObject {
            put("videoId", videoId)
            put("session", readyHolder.sessionToken)
            put("transport", "http-segments")
            put("protocol", HTTP_SEGMENTS_PROTOCOL)
            put("startTimeMs", startTimeMs)
            put(
                "durationMs",
                max(readyHolder.audioFormat.approxDurationMs, readyHolder.videoFormat.approxDurationMs),
            )
            putJsonObject("audio") { putFormat(readyHolder.audioFormat) }
            putJsonObject("video") { putFormat(readyHolder.videoFormat) }
            putJsonObject("endpoints") {
                put("hls", "/sabr/manifest/$videoId?format=hls&session=${readyHolder.sessionToken}")
                put("dash", "/sabr/manifest/$videoId?session=${readyHolder.sessionToken}")
                put("audioInit", initPath(videoId, readyHolder.audioFormat, readyHolder.sessionToken))
                put("videoInit", initPath(videoId, readyHolder.videoFormat, readyHolder.sessionToken))
            }
        })
    }

    private suspend fun preflightOrRecreate(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
        holder: SabrSessionHolder,
    ): SabrSessionHolder? {
        if (preflight(holder, startTimeMs)) return holder
        sabrSessionStore.release(holder)
        val refreshed = sabrSessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = false) ?: prepared
        val refreshedAudio = SabrFormatSelector.audio(refreshed.info, audio.itag, audio.audioTrackId, requireAac = true)
            ?: return null
        val refreshedVideo = SabrFormatSelector.video(refreshed.info, video.itag) ?: return null
        val fresh = createHolder(videoId, userId, refreshed, refreshedAudio, refreshedVideo, startTimeMs)
        return fresh.takeIf { preflight(it, startTimeMs) }
    }

    private suspend fun preflight(holder: SabrSessionHolder, startTimeMs: Long): Boolean =
        withTimeoutOrNull(PREFLIGHT_TIMEOUT_MS) { sabrSessionStore.preflightPlayback(holder, startTimeMs) } == true

    private fun createHolder(
        videoId: String,
        userId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        startTimeMs: Long,
    ): SabrSessionHolder = sabrSessionStore.getOrCreate(
        videoId,
        userId,
        prepared.info,
        audio,
        video,
        prepared.initialToken,
        startTimeMs,
        startPump = false,
    )

    private fun kotlinx.serialization.json.JsonObjectBuilder.putFormat(format: YoutubeSabrFormat): Unit {
        put("itag", format.itag)
        put("mimeType", format.mimeType)
        put("bitrate", format.bitrate)
        put("approxDurationMs", format.approxDurationMs)
        put("qualityLabel", format.qualityLabel)
        put("audioTrackId", format.audioTrackId)
        put("audioTrackName", format.audioTrackDisplayName)
        put("init", initPath("{videoId}", format, "{session}"))
    }

    private fun initPath(videoId: String, format: YoutubeSabrFormat, session: String): String =
        "/sabr/$videoId/${format.itag}/init?session=$session"

    private companion object {
        const val HTTP_SEGMENTS_PROTOCOL = "typetype-sabr-http-v1"
        const val PREFLIGHT_TIMEOUT_MS = 25_000L
    }
}
