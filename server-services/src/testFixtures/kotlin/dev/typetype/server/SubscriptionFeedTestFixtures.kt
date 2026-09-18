package dev.typetype.server

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem

object SubscriptionFeedTestFixtures {
    fun video(
        uploaded: Long,
        channel: String = "Ch",
        url: String = "u/$uploaded",
        short: Boolean = false,
        live: Boolean = false,
    ): VideoItem = VideoItem(
        id = "id-$uploaded-$channel",
        title = "V$uploaded",
        url = url,
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = if (short) 40L else 300L,
        viewCount = 0L,
        uploadDate = "",
        uploaded = uploaded,
        streamType = if (live) "live_stream" else "video_stream",
        isShortFormContent = short,
        uploaderVerified = false,
        shortDescription = null,
        isLive = live,
        isLiveContent = live,
    )

    fun channel(vararg videos: VideoItem): ExtractionResult<ChannelResponse> = ExtractionResult.Success(
        ChannelResponse("Test", "", "", "", 0L, false, videos.toList(), null),
    )

    fun subscription(index: Int): SubscriptionItem = subscription("https://yt.com/c/$index", "C$index")

    fun subscription(url: String, name: String): SubscriptionItem = SubscriptionItem(url, name, "")
}
