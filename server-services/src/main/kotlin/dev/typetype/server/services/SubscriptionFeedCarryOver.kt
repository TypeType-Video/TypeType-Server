package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

class SubscriptionFeedCarryOver(previous: SubscriptionFeedSnapshot?) {
    private val videosByChannel: Map<String, List<VideoItem>> = previous?.let(::indexByChannel).orEmpty()

    fun videos(channelUrl: String): List<VideoItem> =
        videosByChannel[ChannelUrlCanonicalizer.canonicalize(channelUrl)].orEmpty()

    private fun indexByChannel(snapshot: SubscriptionFeedSnapshot): Map<String, List<VideoItem>> {
        val index = linkedMapOf<String, MutableList<VideoItem>>()
        snapshot.videos.forEach { video ->
            val channels = snapshot.sourceChannelUrls[video.subscriptionFeedKey()] ?: listOf(video.uploaderUrl)
            channels.map(ChannelUrlCanonicalizer::canonicalize).distinct().forEach { channel ->
                index.getOrPut(channel, ::mutableListOf).add(video)
            }
        }
        return index
    }
}
