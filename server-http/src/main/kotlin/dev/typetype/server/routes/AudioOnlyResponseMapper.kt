package dev.typetype.server.routes

import dev.typetype.server.models.AudioOnlyStreamResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AudioOnlyStreamKind
import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.PublicHlsManifestTokenService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun AudioOnlyStreamSelection.toResponse(
    tokenService: AudioOnlyMediaTokenService,
    hlsTokenService: PublicHlsManifestTokenService?,
    userId: String?,
    url: String,
    preferOriginal: Boolean,
    preferredLocale: String?,
): ExtractionResult<AudioOnlyStreamResponse> {
    val token = tokenService.createToken(userId, url, preferOriginal, preferredLocale, stream.itag, stream.audioTrackId)
    val src = when (kind) {
        AudioOnlyStreamKind.Progressive -> "/streams/audio-only/source?token=${encode(token)}"
        AudioOnlyStreamKind.Hls -> hlsTokenService?.createPath(stream.url)
            ?: return ExtractionResult.Failure("No audio-only stream is available", "no_playable_streams")
        AudioOnlyStreamKind.Dash -> stream.manifestUrl?.let { audioOnlyDashManifest(it) }
            ?: return ExtractionResult.Failure("No audio-only stream is available", "no_playable_streams")
        AudioOnlyStreamKind.SabrHls -> stream.manifestUrl?.let { audioOnlySabrHlsManifest(it, token) }
            ?: return ExtractionResult.Failure("No audio-only stream is available", "no_playable_streams")
    }
    return ExtractionResult.Success(AudioOnlyStreamResponse(
        src = src,
        kind = kind.apiValue,
        mimeType = when (kind) {
            AudioOnlyStreamKind.Hls, AudioOnlyStreamKind.SabrHls -> HLS_MIME_TYPE
            AudioOnlyStreamKind.Dash -> DASH_MIME_TYPE
            AudioOnlyStreamKind.Progressive -> stream.mimeType
        },
        codec = stream.codec,
        bitrate = stream.bitrate,
        contentLength = stream.contentLength.takeIf { kind == AudioOnlyStreamKind.Progressive && it > 0 },
        duration = response.duration.takeIf { it > 0 },
    ))
}

private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun audioOnlyDashManifest(manifestUrl: String): String =
    manifestUrl + if (manifestUrl.contains("?")) "&audioOnly=true" else "?audioOnly=true"

private fun audioOnlySabrHlsManifest(manifestUrl: String, token: String): String {
    val separator = if (manifestUrl.contains("?")) "&" else "?"
    return "$manifestUrl${separator}audioOnly=true&format=hls&audioToken=${encode(token)}"
}

private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
private const val DASH_MIME_TYPE = "application/dash+xml"
