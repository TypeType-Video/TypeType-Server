package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.CachedChannelService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.PublicCacheKey
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedChannelServiceTest {
    @Test
    fun `bilibili channel requests bypass old empty cache entries`() = runBlocking {
        val url = "https://space.bilibili.com/23630128"
        val cache = RecordingCache()
        val oldKey = PublicCacheKey.of("channel", url, null, null)
        cache.set(oldKey, CacheJson.encodeToString(ChannelResponse.serializer(), channel()), 3_600)
        val videos = listOf(testVideoItem())
        var calls = 0
        val service = CachedChannelService(channelService { _, _, _ ->
            calls += 1
            ExtractionResult.Success(channel(videos))
        }, cache)

        val result = service.getChannel(url, null)

        assertEquals(videos, (result as ExtractionResult.Success).data.videos)
        assertEquals(1, calls)
        assertEquals(3_600L, cache.ttl(PublicCacheKey.of("channel-bilibili-v3", url, null, null)))
    }

    @Test
    fun `empty first-page Bilibili results receive a short cache ttl`() = runBlocking {
        val url = "https://space.bilibili.com/23630128"
        val cache = RecordingCache()
        val service = CachedChannelService(
            channelService { _, _, _ -> ExtractionResult.Success(channel()) },
            cache,
        )

        assertEquals(ExtractionResult.Success(channel()), service.getChannel(url, null))
        assertEquals(30L, cache.ttl(PublicCacheKey.of("channel-bilibili-v3", url, null, null)))
    }

    @Test
    fun `empty first channel pages from any provider receive a short cache ttl`() = runBlocking {
        val url = "https://www.youtube.com/@empty"
        val cache = RecordingCache()
        val service = CachedChannelService(
            channelService { _, _, _ -> ExtractionResult.Success(channel()) },
            cache,
        )

        assertEquals(ExtractionResult.Success(channel()), service.getChannel(url, null))
        assertEquals(30L, cache.ttl(PublicCacheKey.of("channel", url, null, null)))
    }

    @Test
    fun `empty live tabs are a normal channel page`() = runBlocking {
        val url = "https://www.youtube.com/@quiet/streams"
        val cache = RecordingCache()
        val service = CachedChannelService(
            channelService { _, _, _ -> ExtractionResult.Success(channel()) },
            cache,
        )

        service.getChannel(url, null)

        assertEquals(300L, cache.ttl(PublicCacheKey.of("channel", url, null, null)))
    }

    @Test
    fun `a channel page without its channel name falls back to the last complete page`() = runBlocking {
        val url = "https://www.youtube.com/@partial/streams"
        val cache = ExpiringFakeCache()
        val complete = channel(listOf(testVideoItem()))
        var next: ChannelResponse = complete
        val service = CachedChannelService(channelService { _, _, _ -> ExtractionResult.Success(next) }, cache)
        service.getChannel(url, null)
        cache.delete(PublicCacheKey.of("channel", url, null, null))
        next = complete.copy(name = "")

        assertEquals(ExtractionResult.Success(complete), service.getChannel(url, null))
    }

    private fun channelService(
        handler: suspend (String, String?, String?) -> ExtractionResult<ChannelResponse>,
    ) = object : ChannelService {
        override suspend fun getChannel(url: String, nextpage: String?, sort: String?) =
            handler(url, nextpage, sort)

        override suspend fun getPlaylists(url: String, nextpage: String?) =
            ExtractionResult.Success(ChannelPlaylistsResponse(emptyList(), null))
    }

    private fun channel(videos: List<VideoItem> = emptyList()) = ChannelResponse(
        name = "Bilibili channel",
        description = "",
        avatarUrl = "",
        bannerUrl = "",
        subscriberCount = 0,
        isVerified = false,
        videos = videos,
        nextpage = null,
    )

    private class RecordingCache : CacheService {
        private val values = mutableMapOf<String, String>()
        private val ttls = mutableMapOf<String, Long>()

        override suspend fun get(key: String): String? = values[key]

        override suspend fun set(key: String, value: String, ttlSeconds: Long) {
            values[key] = value
            ttls[key] = ttlSeconds
        }

        override suspend fun delete(key: String) {
            values.remove(key)
            ttls.remove(key)
        }

        fun ttl(key: String): Long? = ttls[key]
    }
}
