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

class SubscriptionFeedBuilder(private val channelVideos: ChannelVideosSource) {
    private val semaphore = Semaphore(SubscriptionFeedSettings.maxConcurrentFetches)

    suspend fun build(
        subscriptions: List<SubscriptionItem>,
        previous: SubscriptionFeedSnapshot? = null,
    ): SubscriptionFeedBuildResult = coroutineScope {
        val carryOver = SubscriptionFeedCarryOver(previous)
        val outcomes = subscriptions.map { subscription ->
            async {
                try {
                    fetchSubscription(subscription.channelUrl, carryOver)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    SubscriptionSourceResult(
                        channelUrl = subscription.channelUrl,
                        videos = carryOver.videos(subscription.channelUrl),
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
                if (current == null || video.isLiveContent && !current.isLiveContent) videosByKey[key] = video
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

    private suspend fun fetchSubscription(
        channelUrl: String,
        carryOver: SubscriptionFeedCarryOver,
    ): SubscriptionSourceResult = coroutineScope {
        val channel = async { fetchVideos(channelUrl) }
        val live = if (isYoutubeUrl(channelUrl)) async { fetchVideos(channelUrl.toLivestreamsTabUrl()) } else null
        val channelResult = channel.await()
        val liveResult = live?.await()
        val fetched = if (liveResult == null) {
            channelResult.videos
        } else {
            channelResult.videos.filterNot(VideoItem::isLive) + liveResult.videos.map { it.asLiveContent() }
        }
        val channelMissing = !channelResult.success || channelResult.videos.isEmpty()
        val liveMissing = if (liveResult == null) channelMissing else !liveResult.success
        val carried = carryOver.videos(channelUrl).filter { if (it.isLiveContent) liveMissing else channelMissing }
        val fetchedKeys = fetched.mapTo(HashSet(), VideoItem::subscriptionFeedKey)
        val videos = fetched + carried.filterNot { it.subscriptionFeedKey() in fetchedKeys }
        val results = listOfNotNull(channelResult, liveResult)
        SubscriptionSourceResult(
            channelUrl = channelUrl,
            videos = mergeVideos(videos),
            successfulSources = results.count { it.success },
            failedSources = results.count { !it.success },
        )
    }

    private suspend fun fetchVideos(url: String): SourceFetchResult = semaphore.withPermit {
        try {
            withTimeoutOrNull(CHANNEL_TIMEOUT_MS) {
                when (val result = channelVideos.firstPage(url)) {
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
            if (current == null || video.isLiveContent && !current.isLiveContent) put(key, video)
        }
    }.values.toList()

    private fun VideoItem.asLiveContent(): VideoItem = if (isLiveContent) this else copy(isLiveContent = true)

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
        private const val CHANNEL_TIMEOUT_MS = 15_000L
        private val YOUTUBE_CHANNEL_TABS = setOf("featured", "videos", "shorts", "streams", "playlists", "community", "about")
    }
}

data class SubscriptionFeedBuildResult(
    val videos: List<VideoItem>,
    val sourceChannelUrls: Map<String, List<String>>,
    val successfulSources: Int,
    val failedSources: Int,
)
