package dev.typetype.server

import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.ChannelService

class FakeChannelService : ChannelService {
    override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> {
        val video = VideoItem(
            id = "id-${url.hashCode()}",
            title = "video",
            url = "$url/video",
            thumbnailUrl = "",
            uploaderName = "uploader",
            uploaderUrl = url,
            uploaderAvatarUrl = "",
            duration = 10L,
            viewCount = 0L,
            uploadDate = "",
            uploaded = System.currentTimeMillis(),
            streamType = "video_stream",
            isShortFormContent = false,
            uploaderVerified = false,
            shortDescription = null,
        )
        return ExtractionResult.Success(
            ChannelResponse(
                name = "name",
                description = "",
                avatarUrl = "",
                bannerUrl = "",
                subscriberCount = 0L,
                isVerified = false,
                videos = listOf(video),
                nextpage = null,
            ),
        )
    }

    override suspend fun getPlaylists(url: String, nextpage: String?): ExtractionResult<ChannelPlaylistsResponse> =
        ExtractionResult.Success(ChannelPlaylistsResponse(playlists = emptyList(), nextpage = null))
}
