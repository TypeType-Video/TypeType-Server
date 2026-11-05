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
    private class RecordingProxy(
        private val bodies: Map<String, String>,
        private val binary: Map<String, ByteArray> = emptyMap(),
    ) : ProviderMediaAwareProxyService {
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
            val body = binary[fileName(url)] ?: (bodies[fileName(url)] ?: "x".repeat(2_000)).toByteArray()
            val ranged = rangeHeader?.let { body.copyOfRange(0, minOf(body.size, it.substringAfter('-').toInt() + 1)) }
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
            videoOnlyStreams = listOf(testVideoStream(url = video).copy(indexEnd = 1807, bitrate = null)),
            audioStreams = listOf(testAudioStream(url = audio).copy(indexEnd = 1731, bitrate = null)),
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
        val target = requireNotNull(handles.resolve(video.substringAfterLast('/')))
        val served = requireNotNull(cache.respond(target.url, "bytes=996-1807"))
        assertEquals("bytes 996-1807/2000", served.contentRange)
    }

    @Test
    fun `fetches every NicoNico playlist and starts only the lowest variant and the audio`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc"
        val paths = listOf("high.m3u8", "low.m3u8", "audio.m3u8", "init.cmfv", "key", "low1.cmfv", "high1.cmfv", "audio1.cmfa")
            .associateWith { handles.createPath("$base/$it?Expires=4000000000").substringAfterLast('/') }
        val bodies = mapOf(
            "low.m3u8" to mediaPlaylist(paths.getValue("init.cmfv"), paths.getValue("key"), paths.getValue("low1.cmfv")),
            "high.m3u8" to mediaPlaylist(paths.getValue("init.cmfv"), paths.getValue("key"), paths.getValue("high1.cmfv")),
            "audio.m3u8" to mediaPlaylist(paths.getValue("init.cmfv"), paths.getValue("key"), paths.getValue("audio1.cmfa")),
        )
        val proxy = RecordingProxy(bodies)
        val cache = ProviderMediaStartupCache()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val warmup = ProviderMediaWarmup(handles, proxy, cache, scope)
        val response = testStreamResponse(
            videoOnlyStreams = listOf(
                testVideoStream(url = "/media/${paths.getValue("high.m3u8")}").copy(height = 720, bitrate = null),
                testVideoStream(url = "/media/${paths.getValue("low.m3u8")}").copy(height = 144, bitrate = null),
            ),
            audioStreams = listOf(testAudioStream(url = "/media/${paths.getValue("audio.m3u8")}")),
        )

        warmup.warm(response, ProviderMediaType.NICONICO).join()

        val fetched = proxy.requests.map { fileName(it.first) }.toSet()
        assertEquals(setOf("high.m3u8", "low.m3u8", "audio.m3u8", "init.cmfv", "key", "low1.cmfv", "audio1.cmfa"), fetched)
        val segment = requireNotNull(handles.resolve(paths.getValue("low1.cmfv")))
        assertNotNull(cache.respond(segment.url, null))
    }

    @Test
    fun `fetches the first Bilibili segment of the starting quality only`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val base = "https://upos-sz-mirrorcosov.bilivideo.com"
        val low = handles.createPath("$base/low.m4s?deadline=4000000000")
        val high = handles.createPath("$base/high.m4s?deadline=4000000000")
        val sidx = sidx(firstSegmentSize = 50_000)
        val file = ByteArray(1_000) + sidx + ByteArray(120_000)
        val proxy = RecordingProxy(emptyMap(), mapOf("low.m4s" to file, "high.m4s" to file))
        val cache = ProviderMediaStartupCache()
        val warmup = ProviderMediaWarmup(handles, proxy, cache, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val indexEnd = 1_000L + sidx.size - 1
        val response = testStreamResponse(
            videoOnlyStreams = listOf(
                testVideoStream(url = high).copy(bitrate = 1_800_000, codec = "avc1.640028", indexStart = 1_000, indexEnd = indexEnd),
                testVideoStream(url = low).copy(bitrate = 800_000, codec = "avc1.64001F", indexStart = 1_000, indexEnd = indexEnd),
            ),
            audioStreams = emptyList(),
        )

        warmup.warm(response, ProviderMediaType.BILIBILI).join()

        val segmentEnd = indexEnd + 50_000
        assertEquals(
            setOf("low.m4s" to "bytes=0-${indexEnd + 700_000}", "high.m4s" to "bytes=0-$indexEnd"),
            proxy.requests.map { fileName(it.first) to it.second }.toSet(),
        )
        val target = requireNotNull(handles.resolve(low.substringAfterLast('/')))
        assertNotNull(cache.respond(target.url, "bytes=${indexEnd + 1}-$segmentEnd"))
    }

    private fun sidx(firstSegmentSize: Int): ByteArray {
        val size = 8 + 4 + 8 + 8 + 4 + 12
        val buffer = java.nio.ByteBuffer.allocate(size)
        buffer.putInt(size).put("sidx".toByteArray()).putInt(0).putInt(1).putInt(1000)
        buffer.putInt(0).putInt(0).putShort(0).putShort(1)
        buffer.putInt(firstSegmentSize).putInt(5000).putInt(0)
        return buffer.array()
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
