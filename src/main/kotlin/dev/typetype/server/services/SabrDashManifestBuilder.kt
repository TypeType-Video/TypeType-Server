package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState

internal object SabrDashManifestBuilder {
    fun build(
        videoId: String,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        endSegmentAudio: Long,
        endSegmentVideo: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        startSegmentAudio: Int = 1,
        startSegmentVideo: Int = 1,
        mediaBasePath: String = "../$videoId",
        extraSegmentQuery: String = "",
    ): String {
        val sb = StringBuilder()
        appendHeader(sb, SabrManifestTiming.videoDurationSec(audio, video, streamState))
        appendVideoAdaptation(sb, mediaBasePath, video, startSegmentVideo, endSegmentVideo, streamState, sessionToken, extraSegmentQuery)
        appendAudioAdaptation(sb, mediaBasePath, audio, startSegmentAudio, endSegmentAudio, streamState, sessionToken, extraSegmentQuery)
        return finish(sb)
    }

    fun buildAudioOnly(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        startSegmentAudio: Int = 1,
        mediaBasePath: String = "../$videoId",
    ): String {
        val sb = StringBuilder()
        appendHeader(sb, SabrManifestTiming.audioDurationSec(audio, streamState))
        appendAudioAdaptation(sb, mediaBasePath, audio, startSegmentAudio, endSegmentAudio, streamState, sessionToken)
        return finish(sb)
    }

    private fun appendHeader(sb: StringBuilder, durationSec: Long) {
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"")
        sb.appendLine("     profiles=\"urn:mpeg:dash:profile:full:2011\"")
        sb.appendLine("     type=\"static\"")
        sb.appendLine("     mediaPresentationDuration=\"PT${durationSec}S\"")
        sb.appendLine("     minBufferTime=\"PT2S\">")
        sb.appendLine("  <Period>")
    }

    private fun finish(sb: StringBuilder): String {
        sb.appendLine("  </Period>")
        sb.append("</MPD>")
        return sb.toString()
    }

    private fun appendVideoAdaptation(
        sb: StringBuilder,
        mediaBasePath: String,
        video: YoutubeSabrFormat,
        startSegment: Int,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        extraSegmentQuery: String,
    ) {
        val (container, codecs) = splitMime(video.mimeType.orEmpty())
        val bandwidth = video.bitrate.coerceAtLeast(1)
        val sizeAttr = videoSizeAttr(video)
        sb.appendLine("    <AdaptationSet mimeType=\"$container\" startWithSAP=\"1\">")
        sb.appendLine("      <Representation id=\"v\" bandwidth=\"$bandwidth\"$sizeAttr codecs=\"$codecs\">")
        appendSegmentList(sb, mediaBasePath, video, startSegment, endSegment, streamState, sessionToken, extraSegmentQuery)
        sb.appendLine("      </Representation>")
        sb.appendLine("    </AdaptationSet>")
    }

    private fun appendAudioAdaptation(
        sb: StringBuilder,
        mediaBasePath: String,
        audio: YoutubeSabrFormat,
        startSegment: Int,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        extraSegmentQuery: String = "",
    ) {
        val (container, codecs) = splitMime(audio.mimeType.orEmpty())
        val bandwidth = audio.bitrate.coerceAtLeast(128_000)
        sb.appendLine("    <AdaptationSet mimeType=\"$container\">")
        sb.appendLine("      <Representation id=\"a\" bandwidth=\"$bandwidth\" codecs=\"$codecs\">")
        appendSegmentList(sb, mediaBasePath, audio, startSegment, endSegment, streamState, sessionToken, extraSegmentQuery)
        sb.appendLine("      </Representation>")
        sb.appendLine("    </AdaptationSet>")
    }

    private fun appendSegmentList(
        sb: StringBuilder,
        mediaBasePath: String,
        format: YoutubeSabrFormat,
        startSegment: Int,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
        extraSegmentQuery: String,
    ) {
        val n = endSegment.coerceAtLeast(1)
        val first = startSegment.coerceAtLeast(1).coerceAtMost(n.toInt())
        val avgMs = SabrManifestTiming.averageSegmentMs(format, n)
        val sessionQuery = "?session=$sessionToken$extraSegmentQuery"
        sb.appendLine("        <SegmentList timescale=\"1000\">")
        sb.appendLine("          <Initialization sourceURL=\"$mediaBasePath/${format.itag}/init$sessionQuery\"/>")
        sb.appendLine("          <SegmentTimeline>")
        for (seq in first..n) {
            val start = streamState.getSegmentStartMs(format, seq.toInt()).coerceAtLeast(0L)
            val duration = SabrManifestTiming.timelineDurationMs(format, seq.toInt(), streamState, avgMs)
            sb.appendLine("            <S t=\"$start\" d=\"$duration\"/>")
        }
        sb.appendLine("          </SegmentTimeline>")
        for (seq in first..n) {
            sb.appendLine("          <SegmentURL media=\"$mediaBasePath/${format.itag}/segment/$seq$sessionQuery\"/>")
        }
        sb.appendLine("        </SegmentList>")
    }
}
