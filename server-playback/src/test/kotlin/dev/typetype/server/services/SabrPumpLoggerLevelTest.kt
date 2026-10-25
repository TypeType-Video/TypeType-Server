package dev.typetype.server.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Instant

class SabrPumpLoggerLevelTest {
    private val logger = LoggerFactory.getLogger(SabrSessionPumpLoop::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @BeforeEach
    fun attach() {
        previousLevel = logger.level
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
        logger.level = previousLevel
    }

    @Test
    fun `demand start and finish are not logged at info`() {
        logger.level = Level.INFO
        val (holder, request) = fixture()

        SabrPumpLogger.start(holder, "demand", request)
        SabrPumpLogger.finish(holder, "demand", request, pumped = 1)

        assertTrue(appender.list.isEmpty())
    }

    @Test
    fun `demand start and finish are logged at debug`() {
        logger.level = Level.DEBUG
        val (holder, request) = fixture()

        SabrPumpLogger.start(holder, "demand", request)
        SabrPumpLogger.finish(holder, "demand", request, pumped = 1)

        assertEquals(listOf(Level.DEBUG, Level.DEBUG), appender.list.map { it.level })
    }

    @Test
    fun `round failures stay at warn`() {
        logger.level = Level.INFO
        val (holder, _) = fixture()

        SabrPumpLogger.failure(holder, IllegalStateException("boom"))

        assertEquals(listOf(Level.WARN), appender.list.map { it.level })
    }

    private fun fixture(): Pair<SabrSessionHolder, SabrSegmentRequest> {
        val format = mockk<YoutubeSabrFormat>(relaxed = true)
        every { format.itag } returns 140
        val request = mockk<SabrSegmentRequest>()
        every { request.format } returns format
        every { request.sequenceNumber } returns 3
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(relaxed = true),
            audioFormat = format,
            videoFormat = format,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", 140, null, 140, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        return holder to request
    }
}
