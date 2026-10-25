package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamExtractionErrorMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliRiskControlException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class StreamExtractionErrorCauseTest {
    @Test
    fun `maps wrapped bot challenges to provider access blocked`() {
        val antiBot = StreamExtractionErrorMapper.map<Any>(
            ExtractionException("Could not get stream info", AntiBotException("confirm you are not a bot")),
        )
        val captcha = StreamExtractionErrorMapper.map<Any>(
            RuntimeException(ExtractionException(ReCaptchaException("captcha", "https://youtube.com"))),
        )

        assertEquals(ExtractionFailureKind.ProviderAccessBlocked, (antiBot as ExtractionResult.Failure).kind)
        assertEquals(StreamExtractionErrorMapper.PROVIDER_ACCESS_BLOCKED_CODE, antiBot.code)
        assertEquals(ExtractionFailureKind.ProviderAccessBlocked, (captcha as ExtractionResult.Failure).kind)
    }

    @Test
    fun `maps bilibili risk control to a rate limit with a retry delay`() {
        val result = StreamExtractionErrorMapper.map<Any>(
            ExtractionException("Could not get stream info", BilibiliRiskControlException("risk control", -352)),
        )

        assertEquals(
            ExtractionResult.Failure(
                StreamExtractionErrorMapper.PROVIDER_RATE_LIMITED_FALLBACK,
                StreamExtractionErrorMapper.PROVIDER_RATE_LIMITED_CODE,
                ExtractionFailureKind.RateLimited,
                60L,
            ),
            result,
        )
    }

    @Test
    fun `maps wrapped content restrictions by their cause`() {
        val result = StreamExtractionErrorMapper.map<Any>(
            ExtractionException("Could not get stream info", GeographicRestrictionException("Only in Japan")),
        )

        assertEquals(ExtractionResult.BadRequest("Only in Japan", "geographic_restriction"), result)
    }

    @Test
    fun `maps socket and call timeouts to upstream timeout`() {
        val socket = StreamExtractionErrorMapper.map<Any>(ExtractionException(SocketTimeoutException("Read timed out")))
        val call = StreamExtractionErrorMapper.map<Any>(InterruptedIOException("timeout"))

        assertEquals(ExtractionFailureKind.UpstreamTimeout, (socket as ExtractionResult.Failure).kind)
        assertEquals(StreamExtractionErrorMapper.UPSTREAM_TIMEOUT_CODE, socket.code)
        assertEquals(ExtractionFailureKind.UpstreamTimeout, (call as ExtractionResult.Failure).kind)
    }

    @Test
    fun `maps missing extractor values to a clear unavailable code`() {
        val result = StreamExtractionErrorMapper.map<Any>(NoSuchElementException("No value present"))

        assertEquals(
            ExtractionResult.Failure(
                StreamExtractionErrorMapper.PROVIDER_DATA_MISSING_FALLBACK,
                StreamExtractionErrorMapper.PROVIDER_DATA_MISSING_CODE,
                ExtractionFailureKind.Unavailable,
            ),
            result,
        )
    }

    @Test
    fun `stops walking self referencing causes`() {
        val error = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }

        val result = StreamExtractionErrorMapper.map<Any>(error)

        assertEquals(StreamExtractionErrorMapper.UPSTREAM_FAILURE_CODE, (result as ExtractionResult.Failure).code)
    }

    @Test
    fun `maps illegal arguments to a deterministic unavailable failure`() {
        val result = StreamExtractionErrorMapper.map<Any>(IllegalArgumentException("unsupported url"))

        assertEquals(ExtractionFailureKind.Unavailable, (result as ExtractionResult.Failure).kind)
        assertEquals(StreamExtractionErrorMapper.UPSTREAM_FAILURE_CODE, result.code)
    }
}
