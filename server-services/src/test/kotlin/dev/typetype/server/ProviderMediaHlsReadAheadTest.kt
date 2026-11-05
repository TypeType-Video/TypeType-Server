package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.services.ProviderMediaAwareProxyService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProviderMediaReadAhead
import dev.typetype.server.services.ProviderMediaSegmentTable
import dev.typetype.server.services.ProviderMediaStartupCache
import dev.typetype.server.services.ProviderMediaType
import dev.typetype.server.services.ProviderMediaWarmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Collections

class ProviderMediaHlsReadAheadTest {
    private class FileProxy(private val bodies: Map<String, String>) : ProviderMediaAwareProxyService {
        val fetched: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun pipeProviderMedia(url: String, rangeHeader: String?, domandBid: String?) = respond(url)

        override suspend fun pipeProviderMediaWithAlternates(
            url: String,
            rangeHeader: String?,
            domandBid: String?,
            alternateUrls: List<String>,
        ) = respond(url)

        private fun respond(url: String): ExtractionResult<ProxyResponse> {
            val name = url.substringBefore('?').substringAfterLast('/')
            fetched += name
            val body = (bodies[name] ?: "segment $name").toByteArray()
            return ExtractionResult.Success(
                ProxyResponse(200, "application/octet-stream", body.size.toLong(), null, "bytes", ByteArrayInputStream(body), close = {}),
            )
        }
    }

    @Test
    fun `serving a NicoNico segment fetches the next one and the audio beside it`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc"
        val names = listOf("video.m3u8", "audio.m3u8", "v0", "v1", "v2", "a0", "a1", "a2", "a3")
        val paths = names.associateWith { handles.createPath("$base/$it?Expires=4000000000").substringAfterLast('/') }
        fun playlist(duration: String, vararg segments: String) = (listOf("#EXTM3U") + segments.flatMap {
            listOf("#EXTINF:$duration,", "../media/${paths.getValue(it)}")
        }).joinToString("\n")
        val proxy = FileProxy(
            mapOf(
                "video.m3u8" to playlist("6.0", "v0", "v1", "v2"),
                "audio.m3u8" to playlist("4.8", "a0", "a1", "a2", "a3"),
            ),
        )
        val cache = ProviderMediaStartupCache()
        val segments = ProviderMediaSegmentTable()
        val warmup = ProviderMediaWarmup(handles, proxy, cache, CoroutineScope(SupervisorJob() + Dispatchers.IO), segments)
        val response = testStreamResponse(
            videoOnlyStreams = listOf(testVideoStream(url = "/media/${paths.getValue("video.m3u8")}")),
            audioStreams = listOf(testAudioStream(url = "/media/${paths.getValue("audio.m3u8")}")),
        )
        warmup.warm(response, ProviderMediaType.NICONICO).join()
        proxy.fetched.clear()

        val served = paths.getValue("v1")
        ProviderMediaReadAhead(warmup, segments).onServed(served, requireNotNull(handles.resolve(served)), null)

        withTimeout(5_000) {
            for (name in listOf("v2", "a1")) {
                val url = requireNotNull(handles.resolve(paths.getValue(name))).url
                while (cache.get(url) == null) delay(10)
            }
        }
        assertEquals(setOf("v2", "a1"), proxy.fetched.toSet())
    }
}
