package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.services.ProviderMediaAwareProxyService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProviderMediaStartupCache
import dev.typetype.server.services.ProviderMediaType
import dev.typetype.server.services.ProviderMediaWarmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Collections

class ProviderMediaWarmupTest {
    private class RecordingProxy(private val bodies: Map<String, String>) : ProviderMediaAwareProxyService {
        val requests: MutableList<Pair<String, String?>> = Collections.synchronizedList(mutableListOf())

        override suspend fun pipeProviderMedia(url: String, rangeHeader: String?, domandBid: String?) =
            respond(url, rangeHeader)

        override suspend fun pipeProviderMediaWithAlternates(
            url: String,
            rangeHeader: String?,
            domandBid: String?,
            alternateUrls: List<String>,
        ) = respond(url, rangeHeader)

        private fun respond(url: String, rangeHeader: String?): ExtractionResult<ProxyResponse> {
            requests += url to rangeHeader
            val body = (bodies[fileName(url)] ?: "x".repeat(2_000)).toByteArray()
            val ranged = rangeHeader?.let { body.copyOfRange(0, it.substringAfter('-').toInt() + 1) }
            return ExtractionResult.Success(
                ProxyResponse(
                    status = if (ranged != null) 206 else 200,
                    contentType = "application/octet-stream",
                    contentLength = (ranged ?: body).size.toLong(),
                    contentRange = ranged?.let { "bytes 0-${it.size - 1}/${body.size}" },
                    acceptRanges = "bytes",
                    stream = ByteArrayInputStream(ranged ?: body),
                    close = {},
                ),
            )
        }
    }

    @Test
    fun `fetches the init and index of every Bilibili stream once`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val video = handles.createPath("https://upos-sz-mirrorcosov.bilivideo.com/video.m4s?deadline=4000000000")
        val audio = handles.createPath("https://upos-sz-mirrorcosov.bilivideo.com/audio.m4s?deadline=4000000000")
        val proxy = RecordingProxy(emptyMap())
        val cache = ProviderMediaStartupCache()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val warmup = ProviderMediaWarmup(handles, proxy, cache, scope)
        val response = testStreamResponse(
            videoOnlyStreams = listOf(testVideoStream(url = video).copy(indexEnd = 1807)),
            audioStreams = listOf(testAudioStream(url = audio).copy(indexEnd = 1731)),
        )

        val first = warmup.warm(response, ProviderMediaType.BILIBILI)
        val second = warmup.warm(response, ProviderMediaType.BILIBILI)
        first.join()
        second.join()

        assertEquals(
            setOf(
                "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s?deadline=4000000000" to "bytes=0-1807",
                "https://upos-sz-mirrorcosov.bilivideo.com/audio.m4s?deadline=4000000000" to "bytes=0-1731",
            ),
            proxy.requests.toSet(),
        )
        assertEquals(2, proxy.requests.size)
        val served = requireNotNull(cache.respond(video.substringAfterLast('/'), "bytes=996-1807"))
        assertEquals("bytes 996-1807/2000", served.contentRange)
    }

    @Test
    fun `follows the NicoNico playlists down to the first segment of the lowest variant`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc"
        val paths = listOf("master.m3u8", "high.m3u8", "low.m3u8", "audio.m3u8", "init.cmfv", "key", "low1.cmfv", "high1.cmfv", "audio1.cmfa")
            .associateWith { handles.createPath("$base/$it?Expires=4000000000").substringAfterLast('/') }
        val bodies = mapOf(
            "master.m3u8" to """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",URI="../media/${paths.getValue("audio.m3u8")}"
                #EXT-X-STREAM-INF:BANDWIDTH=3000000,AUDIO="a"
                ../media/${paths.getValue("high.m3u8")}
                #EXT-X-STREAM-INF:BANDWIDTH=500000,AUDIO="a"
                ../media/${paths.getValue("low.m3u8")}
            """.trimIndent(),
            "low.m3u8" to mediaPlaylist(paths.getValue("init.cmfv"), paths.getValue("key"), paths.getValue("low1.cmfv")),
            "high.m3u8" to mediaPlaylist(paths.getValue("init.cmfv"), paths.getValue("key"), paths.getValue("high1.cmfv")),
            "audio.m3u8" to mediaPlaylist(paths.getValue("init.cmfv"), paths.getValue("key"), paths.getValue("audio1.cmfa")),
        )
        val proxy = RecordingProxy(bodies)
        val cache = ProviderMediaStartupCache()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val warmup = ProviderMediaWarmup(handles, proxy, cache, scope)

        val response = testStreamResponse(
            videoOnlyStreams = emptyList(),
            audioStreams = emptyList(),
            hlsUrl = "/media/${paths.getValue("master.m3u8")}",
        )
        warmup.warm(response, ProviderMediaType.NICONICO).join()

        val fetched = proxy.requests.map { fileName(it.first) }.toSet()
        assertEquals(setOf("master.m3u8", "high.m3u8", "low.m3u8", "audio.m3u8", "init.cmfv", "key", "low1.cmfv", "audio1.cmfa"), fetched)
        assertNotNull(cache.respond(paths.getValue("low1.cmfv"), null))
    }

    private companion object {
        fun fileName(url: String): String = url.substringBefore('?').substringAfterLast('/')
    }

    private fun mediaPlaylist(init: String, key: String, segment: String) = """
        #EXTM3U
        #EXT-X-KEY:METHOD=AES-128,URI="../media/$key"
        #EXT-X-MAP:URI="../media/$init"
        #EXTINF:6.0,
        ../media/$segment
    """.trimIndent()
}
