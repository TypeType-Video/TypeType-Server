package dev.typetype.server

import dev.typetype.server.services.BilibiliMirrorProber
import dev.typetype.server.services.BilibiliMirrorSpeedTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.util.Collections

class BilibiliMirrorProberTest {
    private val ranges = Collections.synchronizedList(mutableListOf<String>())
    private val supervisor = SupervisorJob()

    @BeforeEach
    fun setup() {
        BilibiliMirrorSpeedTracker.reset()
        ranges.clear()
    }

    @AfterEach
    fun tearDown() {
        supervisor.cancelChildren()
        BilibiliMirrorSpeedTracker.reset()
    }

    private fun client(slowHost: String) = OkHttpClient.Builder()
        .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
        .addInterceptor { chain ->
            val request = chain.request()
            ranges += "${request.url.host} ${request.header("Range")}"
            if (request.url.host == slowHost) Thread.sleep(300)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .body(ByteArray(128 * 1024).toResponseBody())
                .build()
        }
        .build()

    @Test
    fun `measures every mirror once in the middle of the file and ranks the quicker first`() = runBlocking {
        val slow = "https://upos-hz-mirrorakam.akamaized.net/v.m4s"
        val quick = "https://upos-sz-mirrorcosov.bilivideo.com/v.m4s"
        val prober = BilibiliMirrorProber(client(slowHost = "upos-hz-mirrorakam.akamaized.net"), CoroutineScope(supervisor + Dispatchers.IO))

        prober.probe(listOf(slow, quick), totalLength = 40_000_000)
        prober.probe(listOf(slow, quick), totalLength = 40_000_000)
        withTimeout(5_000) {
            while (BilibiliMirrorSpeedTracker.rankByExpectedTime(listOf(slow, quick)).first() != quick) delay(20)
        }

        assertEquals(2, ranges.size)
        ranges.forEach { line ->
            val start = line.substringAfter("bytes=").substringBefore('-').toLong()
            assertTrue(start in 10_000_000..20_000_000, line)
        }
    }

    @Test
    fun `skips probing mirrors that already have fresh measurements from organic playback`() = runBlocking {
        val fresh = "https://upos-sz-mirrorcosov.bilivideo.com/v.m4s"
        val unknown = "https://upos-hz-mirrorakam.akamaized.net/v.m4s"
        BilibiliMirrorSpeedTracker.recordThroughput(fresh, 3_000_000.0)

        val prober = BilibiliMirrorProber(client(slowHost = ""), CoroutineScope(supervisor + Dispatchers.IO))
        prober.probe(listOf(fresh, unknown), totalLength = 40_000_000)
        delay(100)

        assertEquals(1, ranges.size)
        assertTrue(ranges[0].startsWith("upos-hz-mirrorakam.akamaized.net"))
    }
}
