package dev.typetype.server.services

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.lang.management.ManagementFactory
import java.time.Duration

@Tag("network")
class YoutubeLiveChatProbeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TYPETYPE_LIVE_CHAT_PROBE", matches = "true")
    fun `receives messages from a real YouTube live for five minutes`() = runBlocking {
        val url = System.getenv("TYPETYPE_LIVE_CHAT_URL")?.trim().orEmpty()
        require(url.isNotBlank()) { "TYPETYPE_LIVE_CHAT_URL must be set" }
        val tokenServiceUrl = requireNotNull(System.getenv("YOUTUBE_TOKEN_SERVICE_URL"))
        NewPipeInitializer.init(tokenServiceUrl)
        val service = PipePipeYoutubeLiveChatService(TypetypeTokenSabrTokenClient(tokenServiceUrl))
        val initialPoolThreads = poolThreadCount()
        val result = service.openSession(url)
        val session = (result as? YoutubeLiveChatOpenResult.Opened)?.session
            ?: error("Could not open live chat: ${result::class.simpleName}")
        val heap = ManagementFactory.getMemoryMXBean()
        val threads = ManagementFactory.getThreadMXBean()
        val startedAtMs = System.currentTimeMillis()
        var messageCount = 0
        var heartbeatCount = 0
        var lastMessageAtMs = 0L
        var nextSampleAtMs = startedAtMs + SAMPLE_INTERVAL_MS

        try {
            val completedNormally = withTimeoutOrNull(PROBE_DURATION_MS) {
                session.events.collect { event ->
                    when (event) {
                        is YoutubeLiveChatEvent.Message -> {
                            messageCount += 1
                            lastMessageAtMs = System.currentTimeMillis()
                        }
                        is YoutubeLiveChatEvent.Error -> error("Live chat returned an error event")
                        YoutubeLiveChatEvent.Heartbeat -> heartbeatCount += 1
                    }
                    val now = System.currentTimeMillis()
                    if (now >= nextSampleAtMs) {
                        val cpuMs = ProcessHandle.current().info().totalCpuDuration()
                            .orElse(Duration.ZERO).toMillis()
                        val idCacheSize = extractorIdCacheSize(session)
                        println(
                            "[live-chat-probe] elapsedSec=${(now - startedAtMs) / 1000} " +
                                "messages=$messageCount heartbeats=$heartbeatCount " +
                                "messageIdleSec=${if (lastMessageAtMs == 0L) -1 else (now - lastMessageAtMs) / 1000} " +
                                "heapMiB=${heap.heapMemoryUsage.used / MEBIBYTE} " +
                                "processCpuSec=${cpuMs / 1000} threads=${threads.threadCount} " +
                                "poolThreads=${poolThreadCount()} extractorIdCache=$idCacheSize",
                        )
                        nextSampleAtMs = now + SAMPLE_INTERVAL_MS
                    }
                }
                true
            }
            assertNull(completedNormally, "The live chat stream ended before the 5-minute window")
            assertTrue(messageCount > 0, "No live chat messages arrived during the probe")
        } finally {
            session.close()
            service.close()
        }

        kotlinx.coroutines.delay(2_000)
        println(
            "[live-chat-probe] closed poolThreadDelta=${poolThreadCount() - initialPoolThreads} " +
                "heapMiB=${heap.heapMemoryUsage.used / MEBIBYTE}",
        )
        assertTrue(poolThreadCount() <= initialPoolThreads, "Live chat leaves an extractor thread running after closure")
    }

    private fun extractorIdCacheSize(session: YoutubeLiveChatSession): Int? = runCatching {
        val extractor = session.javaClass.getDeclaredField("extractor").apply { isAccessible = true }
            .get(session)
        extractor.javaClass.getDeclaredField("IDList").apply { isAccessible = true }
            .get(extractor) as Collection<*>
    }.getOrNull()?.size

    private fun poolThreadCount(): Int = Thread.getAllStackTraces().keys.count {
        it.name.startsWith("pool-")
    }

    private companion object {
        const val PROBE_DURATION_MS = 5 * 60 * 1_000L
        const val SAMPLE_INTERVAL_MS = 60 * 1_000L
        const val MEBIBYTE = 1_048_576L
    }
}
