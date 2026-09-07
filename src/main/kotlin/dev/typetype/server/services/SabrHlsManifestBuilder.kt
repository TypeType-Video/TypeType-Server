package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.util.Locale

internal object SabrHlsManifestBuilder {
    fun buildMaster(
        videoId: String,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        sessionToken: String,
    ): String {
        val audioCodec = splitMime(audio.mimeType.orEmpty()).second
        val videoCodec = splitMime(video.mimeType.orEmpty()).second
        val codecs = listOf(videoCodec, audioCodec).filter { it.isNotBlank() }.joinToString(",")
        val resolution = if (video.width > 0 && video.height > 0) "RESOLUTION=${video.width}x${video.height}," else ""
        val bandwidth = (video.bitrate + audio.bitrate).coerceAtLeast(1)
        val base = "?format=hls&session=$sessionToken"
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        sb.appendLine("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"audio\",DEFAULT=YES,AUTOSELECT=YES,URI=\"$base&playlist=audio\"")
        sb.appendLine("#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,${resolution}CODECS=\"$codecs\",AUDIO=\"audio\"")
        sb.appendLine("$base&playlist=video")
        return sb.toString()
    }

    fun buildAudioOnly(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String {
        val n = endSegmentAudio.coerceAtLeast(1L).toInt()
        val durations = (1..n).map { seq -> SabrManifestTiming.segmentDurationMs(audio, seq, n, streamState) }
        val sessionQuery = "?session=$sessionToken"
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-TARGETDURATION:${targetDuration(durations)}")
        sb.appendLine("#EXT-X-MEDIA-SEQUENCE:1")
        sb.appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        sb.appendLine("#EXT-X-MAP:URI=\"../$videoId/${audio.itag}/init$sessionQuery\"")
        for (seq in 1..n) {
            sb.appendLine("#EXTINF:${String.format(Locale.US, "%.3f", durations[seq - 1] / 1000.0)},")
            sb.appendLine("../$videoId/${audio.itag}/segment/$seq$sessionQuery")
        }
        sb.appendLine("#EXT-X-ENDLIST")
        return sb.toString()
    }

    fun buildVideoOnly(
        videoId: String,
        video: YoutubeSabrFormat,
        endSegmentVideo: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String = buildMediaPlaylist(videoId, video, endSegmentVideo, streamState, sessionToken)

    private fun buildMediaPlaylist(
        videoId: String,
        format: YoutubeSabrFormat,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String {
        val n = endSegment.coerceAtLeast(1L).toInt()
        val durations = (1..n).map { seq -> SabrManifestTiming.segmentDurationMs(format, seq, n, streamState) }
        val sessionQuery = "?session=$sessionToken"
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-TARGETDURATION:${targetDuration(durations)}")
        sb.appendLine("#EXT-X-MEDIA-SEQUENCE:1")
        sb.appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        sb.appendLine("#EXT-X-MAP:URI=\"../$videoId/${format.itag}/init$sessionQuery\"")
        for (seq in 1..n) {
            sb.appendLine("#EXTINF:${String.format(Locale.US, "%.3f", durations[seq - 1] / 1000.0)},")
            sb.appendLine("../$videoId/${format.itag}/segment/$seq$sessionQuery")
        }
        sb.appendLine("#EXT-X-ENDLIST")
        return sb.toString()
    }

    private fun targetDuration(durations: List<Long>): Long =
        durations.maxOrNull()?.let { ((it + 999L) / 1000L).coerceAtLeast(1L) } ?: 1L
}
