package dev.typetype.server

import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import dev.typetype.server.services.streamCacheTtlSeconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreamCacheTtlResolverTest {

    @Test
    fun `default stream ttl is six hours`() {
        assertEquals(21_600L, response("https://example.com/video.mp4").streamCacheTtlSeconds(nowEpochSeconds = 1_000L))
    }

    @Test
    fun `stream ttl is short when dislike count is unavailable`() {
        assertEquals(300L, response("https://example.com/video.mp4", dislikeCount = -1L).streamCacheTtlSeconds(nowEpochSeconds = 1_000L))
    }

    @Test
    fun `bilibili stream ttl follows signed deadline`() {
        val url = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=10000&upsig=x"
        assertEquals(1_700L, response(url).streamCacheTtlSeconds(nowEpochSeconds = 8_000L))
    }

    @Test
    fun `bilibili stream ttl is capped`() {
        val url = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=20000&upsig=x"
        assertEquals(3_600L, response(url).streamCacheTtlSeconds(nowEpochSeconds = 8_000L))
    }

    @Test
    fun `expired bilibili urls are not cached`() {
        val url = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=8100&upsig=x"
        assertEquals(0L, response(url).streamCacheTtlSeconds(nowEpochSeconds = 8_000L))
    }

    @Test
    fun `niconico stream ttl follows signed expiry`() {
        val url = "https://asset.domand.nicovideo.jp/video/01.cmfv?Expires=10000"
        assertEquals(1_700L, response(url).streamCacheTtlSeconds(nowEpochSeconds = 8_000L))
    }

    private fun response(url: String, dislikeCount: Long = 0L): StreamResponse = StreamResponse(
        id = "id",
        title = "title",
        uploaderName = "uploader",
        uploaderUrl = "",
        uploaderAvatarUrl = "",
        thumbnailUrl = "",
        description = "",
        duration = 1L,
        viewCount = 0L,
        likeCount = 0L,
        dislikeCount = dislikeCount,
        uploadDate = "",
        uploaded = -1L,
        uploaderSubscriberCount = 0L,
        uploaderVerified = false,
        category = "",
        license = "",
        visibility = "",
        tags = emptyList(),
        streamType = "video_stream",
        isShortFormContent = false,
        requiresMembership = false,
        startPosition = 0L,
        streamSegments = emptyList(),
        hlsUrl = "",
        dashMpdUrl = "",
        videoStreams = emptyList(),
        audioStreams = emptyList(),
        originalAudioTrackId = null,
        preferredDefaultAudioTrackId = null,
        videoOnlyStreams = listOf(video(url)),
        subtitles = emptyList(),
        previewFrames = emptyList(),
        sponsorBlockSegments = emptyList(),
        relatedStreams = emptyList(),
    )

    private fun video(url: String): VideoStreamItem = VideoStreamItem(
        url = url,
        mimeType = "video/mp4",
        format = "MPEG_4",
        resolution = "360p",
        bitrate = null,
        codec = null,
        isVideoOnly = true,
        itag = -1,
        width = 0,
        height = 0,
        fps = 0,
        contentLength = 0L,
        initStart = 0L,
        initEnd = 0L,
        indexStart = 0L,
        indexEnd = 0L,
    )
}
