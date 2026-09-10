package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoStreamItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ManifestService(
    private val streamService: StreamService,
    private val providerMediaHandleService: ProviderMediaHandleService? = null,
) {
    suspend fun dashManifest(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result !is ExtractionResult.Success) return result.recast()
        val info = result.data
        val videos = compatibleVideoStreams(info.videoOnlyStreams)
        val audios = compatibleAudioStreams(info.audioStreams, info.preferredDefaultAudioTrackId)
        if (videos.isEmpty() && audios.isEmpty())
            return ExtractionResult.Failure("No compatible streams found for DASH manifest")
        return ExtractionResult.Success(buildMpd(videos, audios, info.duration))
    }

    private fun compatibleVideoStreams(streams: List<VideoStreamItem>): List<VideoStreamItem> =
        streams.filter(::isDashManifestVideoStream)
            .sortedWith(compareBy({ codecPriority(it.codec ?: "") }, { -(it.bitrate ?: bwFromUrl(it.url) ?: 0) }))
    private fun compatibleAudioStreams(streams: List<AudioStreamItem>, preferredTrackId: String?): List<AudioStreamItem> =
        streams.filter { it.url.isNotBlank() && !it.codec.isNullOrBlank() }
            .sortedWith(compareBy<AudioStreamItem> { preferredTrackId != null && it.audioTrackId != preferredTrackId }
                .thenByDescending { it.bitrate ?: 0 })

    private fun codecPriority(codec: String): Int = when {
        codec.startsWith("avc1") -> 0
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 1
        else -> 2
    }

    private suspend fun buildMpd(videos: List<VideoStreamItem>, audios: List<AudioStreamItem>, duration: Long): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"")
        sb.appendLine("     profiles=\"urn:mpeg:dash:profile:full:2011\"")
        sb.appendLine("     type=\"static\"")
        sb.appendLine("     mediaPresentationDuration=\"PT${duration}S\"")
        sb.appendLine("     minBufferTime=\"PT4S\">")
        sb.appendLine("  <Period>")
        videos.groupBy { videoMimeType(it.format) to codecFamily(it.codec ?: "") }
            .forEach { (key, streams) -> appendVideoAdaptationSet(sb, key.first, streams) }
        if (audios.any { it.audioTrackId != null })
            audios.groupBy { it.audioTrackId to audioMimeType(it.format) }
                .forEach { (_, g) -> appendAudioAdaptationSet(sb, audioMimeType(g.first().format), g.first().audioLocale, g.first().audioTrackName, g) }
        else
            audios.groupBy { audioMimeType(it.format) }
                .forEach { (mime, g) -> appendAudioAdaptationSet(sb, mime, null, null, g) }
        sb.appendLine("  </Period>")
        sb.append("</MPD>")
        return sb.toString()
    }

    private suspend fun appendVideoAdaptationSet(sb: StringBuilder, mimeType: String, streams: List<VideoStreamItem>) {
        sb.appendLine("    <AdaptationSet mimeType=\"$mimeType\" startWithSAP=\"1\">")
        streams.forEachIndexed { i, s ->
            val height = if (s.height > 0) s.height else resolutionHeight(s.resolution)
            val width = if (s.width > 0) s.width else if (height > 0) height * 16 / 9 else 0
            val bandwidth = (s.bitrate ?: bwFromUrl(s.url) ?: (height * 1000)).coerceAtLeast(1)
            val sizeAttr = if (width > 0 && height > 0) " width=\"$width\" height=\"$height\"" else ""
            sb.appendLine("      <Representation id=\"${s.manifestRepresentationId(i)}\" bandwidth=\"$bandwidth\"$sizeAttr codecs=\"${s.codec ?: ""}\">")
            sb.appendLine("        <BaseURL>${mediaUrl(s.url)}</BaseURL>")
            if (s.indexStart > 0L && s.indexEnd > 0L) {
                sb.appendLine("        <SegmentBase indexRange=\"${s.indexStart}-${s.indexEnd}\">")
                sb.appendLine("          <Initialization range=\"${s.initStart}-${s.initEnd}\"/>")
                sb.appendLine("        </SegmentBase>")
            }
            sb.appendLine("      </Representation>")
        }
        sb.appendLine("    </AdaptationSet>")
    }

    private suspend fun appendAudioAdaptationSet(sb: StringBuilder, mimeType: String, lang: String?, label: String?, streams: List<AudioStreamItem>) {
        val attrs = "${if (lang != null) " lang=\"$lang\"" else ""}${if (label != null) " label=\"$label\"" else ""}"
        sb.appendLine("    <AdaptationSet mimeType=\"$mimeType\"$attrs>")
        streams.forEachIndexed { i, a ->
            sb.appendLine("      <Representation id=\"${a.manifestRepresentationId(i)}\" bandwidth=\"${((a.bitrate ?: 128) * 1000).coerceAtLeast(1)}\" codecs=\"${normalizeAudioCodec(a.codec)}\">")
            sb.appendLine("        <BaseURL>${mediaUrl(a.url)}</BaseURL>")
            if (a.indexStart > 0L && a.indexEnd > 0L) {
                sb.appendLine("        <SegmentBase indexRange=\"${a.indexStart}-${a.indexEnd}\">")
                sb.appendLine("          <Initialization range=\"${a.initStart}-${a.initEnd}\"/>")
                sb.appendLine("        </SegmentBase>")
            }
            sb.appendLine("      </Representation>")
        }
        sb.appendLine("    </AdaptationSet>")
    }

    private fun normalizeAudioCodec(codec: String?): String = if (codec == "mp4a") "mp4a.40.2" else codec ?: ""

    private fun videoMimeType(format: String): String =
        if (format.lowercase().contains("webm")) "video/webm" else "video/mp4"

    private fun audioMimeType(format: String): String =
        if (format.lowercase().contains("webm")) "audio/webm" else "audio/mp4"

    private fun codecFamily(codec: String): String = when {
        codec.startsWith("avc1") -> "avc"
        codec.startsWith("vp9") || codec.startsWith("vp09") -> "vp9"
        codec.startsWith("av01") -> "av1"
        else -> "other"
    }

    private fun resolutionHeight(resolution: String): Int =
        Regex("(\\d+)[pP]").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun bwFromUrl(url: String): Int? =
        Regex("[?&]bw=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()

    private suspend fun mediaUrl(url: String): String {
        val service = providerMediaHandleService
        if (service == null) return "../proxy?url=${encode(url)}"
        if (url.startsWith("/media/")) return service.relativeManifestPath(url)
        val provider = runCatching { requireProxyTarget(url).provider }.getOrNull()
        return if (provider == ProxyProvider.BILIBILI || provider == ProxyProvider.NICONICO) {
            service.relativeManifestPath(service.createPath(url))
        } else {
            "../proxy?url=${encode(url)}"
        }
    }

    private fun encode(url: String): String =
        URLEncoder.encode(url, StandardCharsets.UTF_8)
    private fun <T> ExtractionResult<T>.recast(): ExtractionResult<String> = when (this) {
        is ExtractionResult.Success -> ExtractionResult.Success(data.toString())
        is ExtractionResult.BadRequest -> ExtractionResult.BadRequest(message)
        is ExtractionResult.Failure -> ExtractionResult.Failure(message)
    }
}
