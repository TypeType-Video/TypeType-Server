package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

internal class SubscriptionFeedBuilder(private val channelService: ChannelService) {
    private val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    suspend fun build(subscriptions: List<SubscriptionItem>): SubscriptionFeedBuildResult = coroutineScope {
        val outcomes = subscriptions.map { subscription ->
            async {
                try {
                    fetchSubscription(subscription.channelUrl)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    SubscriptionSourceResult(
                        channelUrl = subscription.channelUrl,
                        videos = emptyList(),
                        successfulSources = 0,
                        failedSources = 1,
                    )
                }
            }
        }.map { it.await() }
        val videosByKey = linkedMapOf<String, VideoItem>()
        val sourceChannelUrls = linkedMapOf<String, MutableSet<String>>()
        outcomes.forEach { outcome ->
            outcome.videos.forEach { video ->
                val key = video.subscriptionFeedKey()
                val current = videosByKey[key]
                if (current == null || video.isLive && !current.isLive) videosByKey[key] = video
                sourceChannelUrls.getOrPut(key, ::linkedSetOf).add(outcome.channelUrl)
            }
        }
        SubscriptionFeedBuildResult(
            videos = videosByKey.values.toList(),
            sourceChannelUrls = sourceChannelUrls.mapValues { it.value.toList() },
            successfulSources = outcomes.sumOf { it.successfulSources },
            failedSources = outcomes.sumOf { it.failedSources },
        )
    }

    private suspend fun fetchSubscription(channelUrl: String): SubscriptionSourceResult = coroutineScope {
        val channel = async { fetchVideos(channelUrl) }
        val live = if (isYoutubeUrl(channelUrl)) async { fetchVideos(channelUrl.toLivestreamsTabUrl()) } else null
        val results = listOfNotNull(channel.await(), live?.await())
        SubscriptionSourceResult(
            channelUrl = channelUrl,
            videos = mergeVideos(results.flatMap { it.videos }),
            successfulSources = results.count { it.success },
            failedSources = results.count { !it.success },
        )
    }

    private suspend fun fetchVideos(url: String): SourceFetchResult = semaphore.withPermit {
        try {
            withTimeoutOrNull(CHANNEL_TIMEOUT_MS) {
                when (val result = channelService.getChannel(url, null)) {
                    is ExtractionResult.Success -> SourceFetchResult(result.data.videos, true)
                    else -> SourceFetchResult(emptyList(), false)
                }
            } ?: SourceFetchResult(emptyList(), false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            SourceFetchResult(emptyList(), false)
        }
    }

    private fun mergeVideos(videos: List<VideoItem>): List<VideoItem> = videos.deduplicated()

    private fun List<VideoItem>.deduplicated(): List<VideoItem> = buildMap<String, VideoItem> {
        this@deduplicated.forEach { video ->
            val key = video.subscriptionFeedKey()
            val current = get(key)
            if (current == null || video.isLive && !current.isLive) put(key, video)
        }
    }.values.toList()

    private fun String.toLivestreamsTabUrl(): String {
        val uri = URI(this)
        val path = uri.path.trimEnd('/')
        val segments = path.split('/').filter(String::isNotBlank)
        val basePath = if (segments.size >= 2 && segments.last() in YOUTUBE_CHANNEL_TABS) {
            path.substringBeforeLast('/')
        } else {
            path
        }
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, "$basePath/streams", null, null).toString()
    }

    private data class SourceFetchResult(val videos: List<VideoItem>, val success: Boolean)
    private data class SubscriptionSourceResult(
        val channelUrl: String,
        val videos: List<VideoItem>,
        val successfulSources: Int,
        val failedSources: Int,
    )

    companion object {
        private const val MAX_CONCURRENT_FETCHES = 20
        private const val CHANNEL_TIMEOUT_MS = 15_000L
        private val YOUTUBE_CHANNEL_TABS = setOf("featured", "videos", "shorts", "streams", "playlists", "community", "about")
    }
}

internal data class SubscriptionFeedBuildResult(
    val videos: List<VideoItem>,
    val sourceChannelUrls: Map<String, List<String>>,
    val successfulSources: Int,
    val failedSources: Int,
)
