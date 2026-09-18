package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

object NativeManifestBuilder {

    fun build(videos: List<VideoStream>, audios: List<AudioStream>, duration: Long, preferredAudioTrackId: String?): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"")
        sb.appendLine("     profiles=\"urn:mpeg:dash:profile:full:2011\"")
        sb.appendLine("     type=\"static\"")
        sb.appendLine("     mediaPresentationDuration=\"PT${duration}S\"")
        sb.appendLine("     minBufferTime=\"PT4S\">")
        sb.appendLine("  <Period>")
        buildVideoAdaptationSets(sb, videos, duration)
        buildAudioAdaptationSets(sb, audios, duration, preferredAudioTrackId)
        sb.appendLine("  </Period>")
        sb.append("</MPD>")
        return sb.toString()
    }

    private fun buildVideoAdaptationSets(sb: StringBuilder, videos: List<VideoStream>, duration: Long) {
        var id = 0
        videos.groupBy { videoMimeType(it.getCodec() ?: "") }
            .forEach { (mime, group) ->
                sb.appendLine("    <AdaptationSet mimeType=\"$mime\" startWithSAP=\"1\">")
                group.forEach { s -> appendVideoRepresentation(sb, s, id++, duration) }
                sb.appendLine("    </AdaptationSet>")
            }
    }

    private fun buildAudioAdaptationSets(
        sb: StringBuilder,
        audios: List<AudioStream>,
        duration: Long,
        preferredAudioTrackId: String?,
    ) {
        if (audios.isEmpty()) return
        var id = 0
        val ordered = if (preferredAudioTrackId == null) audios else audios.sortedBy {
            it.getAudioTrackId() != preferredAudioTrackId
        }
        ordered.groupBy { audioMimeType(it.getCodec() ?: "") }
            .forEach { (mime, group) ->
                sb.appendLine("    <AdaptationSet mimeType=\"$mime\">")
                group.forEach { s -> appendAudioRepresentation(sb, s, id++, duration) }
                sb.appendLine("    </AdaptationSet>")
            }
    }

    private fun appendVideoRepresentation(sb: StringBuilder, s: VideoStream, i: Int, duration: Long) {
        val itagItem = s.getItagItem() ?: return
        val bandwidth = s.getBitrate().coerceAtLeast(1)
        val width = s.getWidth().takeIf { it > 0 }
        val height = s.getHeight().takeIf { it > 0 }
        val sizeAttr = if (width != null && height != null) " width=\"$width\" height=\"$height\"" else ""
        sb.appendLine("      <Representation id=\"${s.manifestRepresentationId(i)}\" bandwidth=\"$bandwidth\"$sizeAttr codecs=\"${s.getCodec() ?: ""}\">")
        if (s.deliveryMethod == DeliveryMethod.DASH) {
            runCatching {
                YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(s.getContent() ?: "", itagItem, duration)
            }.onSuccess { manifest ->
                sb.append(extractRepresentationInner(rewriteManifestUrls(manifest)))
            }.onFailure {
                appendProgressiveVideoBody(sb, s)
            }
        } else {
            appendProgressiveVideoBody(sb, s)
        }
        sb.appendLine("      </Representation>")
    }

    private fun appendProgressiveVideoBody(sb: StringBuilder, s: VideoStream) {
        sb.appendLine("        <BaseURL>../proxy?url=${encodeUrl(s.getContent() ?: "")}</BaseURL>")
        val indexStart = s.getIndexStart()
        val indexEnd = s.getIndexEnd()
        if (indexStart > 0 && indexEnd > 0) {
            sb.appendLine("        <SegmentBase indexRange=\"$indexStart-$indexEnd\">")
            sb.appendLine("          <Initialization range=\"${s.getInitStart()}-${s.getInitEnd()}\"/>")
            sb.appendLine("        </SegmentBase>")
        }
    }

    private fun appendAudioRepresentation(sb: StringBuilder, s: AudioStream, i: Int, duration: Long) {
        val itagItem = s.getItagItem() ?: return
        val bandwidth = ((s.averageBitrate.takeIf { it > 0 } ?: 128) * 1000).coerceAtLeast(1)
        sb.appendLine("      <Representation id=\"${s.manifestRepresentationId(i)}\" bandwidth=\"$bandwidth\" codecs=\"${s.getCodec() ?: ""}\">")
        runCatching {
            YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(s.getContent() ?: "", itagItem, duration)
        }.onSuccess { manifest ->
            sb.append(extractRepresentationInner(rewriteManifestUrls(manifest)))
        }.onFailure {
            appendProgressiveAudioBody(sb, s)
        }
        sb.appendLine("      </Representation>")
    }

    private fun appendProgressiveAudioBody(sb: StringBuilder, s: AudioStream) {
        sb.appendLine("        <BaseURL>../proxy?url=${encodeUrl(s.getContent() ?: "")}</BaseURL>")
        val indexStart = s.getIndexStart()
        val indexEnd = s.getIndexEnd()
        if (indexStart > 0 && indexEnd > 0) {
            sb.appendLine("        <SegmentBase indexRange=\"$indexStart-$indexEnd\">")
            sb.appendLine("          <Initialization range=\"${s.getInitStart()}-${s.getInitEnd()}\"/>")
            sb.appendLine("        </SegmentBase>")
        }
    }

    private fun extractRepresentationInner(manifest: String): String {
        val repr = REPRESENTATION_REGEX.find(manifest)?.value ?: return ""
        return repr.removePrefix(repr.substringBefore(">") + ">").removeSuffix("</Representation>")
    }
}
