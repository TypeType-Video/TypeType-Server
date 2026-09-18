package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe

class YoutubePlayerClientStreamServiceTest {
    @Test
    fun `classic extraction selects visionOS and restores mweb`() = runBlocking {
        NewPipe.setYoutubePlayerClient(YoutubePlayerClient.MWEB.value)
        val observed = mutableListOf<String>()
        val delegate = recordingService(observed)
        val service = YoutubePlayerClientStreamService(delegate, YoutubePlayerClient.VISIONOS)

        service.getStreamInfo(YOUTUBE_URL)

        assertEquals(listOf(YoutubePlayerClient.VISIONOS.value), observed)
        assertEquals(YoutubePlayerClient.MWEB.value, NewPipe.getYoutubePlayerClient())
    }

    @Test
    fun `sabr extractions stay concurrent while classic waits`() = runBlocking {
        NewPipe.setYoutubePlayerClient(YoutubePlayerClient.MWEB.value)
        val sabrEntered = Channel<Unit>(capacity = 2)
        val releaseSabr = CompletableDeferred<Unit>()
        val classicEntered = AtomicBoolean(false)
        val observations = Collections.synchronizedList(mutableListOf<String>())
        val delegate = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                val client = NewPipe.getYoutubePlayerClient()
                observations += client
                if (client == YoutubePlayerClient.MWEB.value) {
                    sabrEntered.send(Unit)
                    releaseSabr.await()
                } else {
                    classicEntered.set(true)
                }
                observations += NewPipe.getYoutubePlayerClient()
                return ExtractionResult.Failure("recorded")
            }
        }
        val sabr = YoutubePlayerClientStreamService(delegate, YoutubePlayerClient.MWEB)
        val classic = YoutubePlayerClientStreamService(delegate, YoutubePlayerClient.VISIONOS)
        val sabrJobs = List(2) { launch { sabr.getStreamInfo(YOUTUBE_URL) } }
        repeat(2) { sabrEntered.receive() }

        val classicResult = async { classic.getStreamInfo(YOUTUBE_URL) }
        delay(100)
        assertFalse(classicEntered.get())
        releaseSabr.complete(Unit)
        sabrJobs.forEach { it.join() }
        classicResult.await()

        assertTrue(classicEntered.get())
        assertEquals(4, observations.count { it == YoutubePlayerClient.MWEB.value })
        assertEquals(2, observations.count { it == YoutubePlayerClient.VISIONOS.value })
        assertEquals(YoutubePlayerClient.MWEB.value, NewPipe.getYoutubePlayerClient())
    }

    private fun recordingService(observed: MutableList<String>): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
            observed += NewPipe.getYoutubePlayerClient()
            return ExtractionResult.Failure("recorded")
        }
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=GlzleRbo5E0"
    }
}
