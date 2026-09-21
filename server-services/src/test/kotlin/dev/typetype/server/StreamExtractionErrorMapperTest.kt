package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.services.StreamExtractionErrorMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

class StreamExtractionErrorMapperTest {
    @Test
    fun `maps membership restrictions using extractor messages when available`() {
        val login = StreamExtractionErrorMapper.map<Any>(NeedLoginException("This video is only available for members"))
        val paid = StreamExtractionErrorMapper.map<Any>(PaidContentException("This video is only available for members"))
        assertEquals(ExtractionResult.BadRequest("This video is only available for members", "members_only"), login)
        assertEquals(ExtractionResult.BadRequest("This video is only available for members", "members_only"), paid)
    }

    @Test
    fun `maps membership restrictions to fallback when extractor message is blank`() {
        val login = StreamExtractionErrorMapper.map<Any>(NeedLoginException(""))
        val paid = StreamExtractionErrorMapper.map<Any>(PaidContentException(""))
        assertEquals(
            ExtractionResult.BadRequest(StreamExtractionErrorMapper.MEMBERS_ONLY_FALLBACK, "members_only"),
            login,
        )
        assertEquals(
            ExtractionResult.BadRequest(StreamExtractionErrorMapper.PAID_CONTENT_FALLBACK, "paid_content"),
            paid,
        )
    }

    @Test
    fun `keeps paid videos distinct from members-only videos`() {
        val result = StreamExtractionErrorMapper.map<Any>(PaidContentException("This video is a paid video"))
        assertEquals(ExtractionResult.BadRequest("This video is a paid video", "paid_content"), result)
    }

    @Test
    fun `maps upcoming premieres to a stable availability code`() {
        val result = StreamExtractionErrorMapper.map<Any>(VideoNotReleaseException("Premieres in 200 days"))
        assertEquals(ExtractionResult.Failure("Premieres in 200 days", "scheduled_premiere"), result)
    }

    @Test
    fun `does not rewrite youtube timeout message to members-only fallback`() {
        val timeout = IllegalStateException("Error occurs when fetching the page. Try increase the loading timeout in Settings.")
        val mapped = StreamExtractionErrorMapper.map<Any>(timeout, sourceUrl = "https://www.youtube.com/watch?v=test")
        assertEquals(ExtractionResult.Failure("Error occurs when fetching the page. Try increase the loading timeout in Settings."), mapped)
    }

    @Test
    fun `maps youtube music premium exception to paid content`() {
        val mapped = StreamExtractionErrorMapper.map<Any>(YoutubeMusicPremiumContentException())
        assertEquals(
            ExtractionResult.BadRequest("This video is a YouTube Music Premium video", "paid_content"),
            mapped,
        )
    }

    @Test
    fun `maps content restrictions to bad request with extractor message`() {
        val result = StreamExtractionErrorMapper.map<Any>(PrivateContentException("private video"))
        assertEquals(ExtractionResult.BadRequest("private video", "private_content"), result)
    }

    @Test
    fun `maps geographic restrictions to a stable access code`() {
        val result = StreamExtractionErrorMapper.map<Any>(GeographicRestrictionException("Only available in Japan"))
        assertEquals(ExtractionResult.BadRequest("Only available in Japan", "geographic_restriction"), result)
    }

    @Test
    fun `maps provider bot blocks to a vpn-aware failure`() {
        val antiBot = StreamExtractionErrorMapper.map<Any>(AntiBotException("YouTube requested CAPTCHA verification"))
        val captcha = StreamExtractionErrorMapper.map<Any>(ReCaptchaException("reCaptcha requested", "https://youtube.com"))

        val expected = ExtractionResult.Failure(
            StreamExtractionErrorMapper.PROVIDER_ACCESS_BLOCKED_FALLBACK,
            "provider_access_blocked",
        )
        assertEquals(expected, antiBot)
        assertEquals(expected, captcha)
    }

    @Test
    fun `maps age restrictions to a stable access code`() {
        val result = StreamExtractionErrorMapper.map<Any>(AgeRestrictedContentException("Sign in to confirm your age"))
        assertEquals(
            ExtractionResult.BadRequest("Sign in is required to verify access to this video", "age_restricted"),
            result,
        )
    }

    @Test
    fun `maps unknown exceptions to failure`() {
        val result = StreamExtractionErrorMapper.map<Any>(IllegalStateException("boom"))
        assertTrue(result is ExtractionResult.Failure)
        assertEquals("boom", (result as ExtractionResult.Failure).message)
        assertEquals(ExtractionFailureKind.Unknown, result.kind)
    }

    @Test
    fun `maps explicit YouTube session rejection without inspecting its message`() {
        val type = runCatching {
            Class.forName("org.schabi.newpipe.extractor.exceptions.YoutubeSessionRejectedException")
        }.getOrNull()
        assumeTrue(type != null, "requires the local updated PipePipeExtractor")
        val rejectionType = type ?: return
        val error = rejectionType.getConstructor(String::class.java).newInstance("arbitrary") as Throwable

        val result = StreamExtractionErrorMapper.map<Any>(error)

        assertTrue(result is ExtractionResult.Failure)
        assertEquals(ExtractionFailureKind.YoutubeSessionRejected, (result as ExtractionResult.Failure).kind)
    }
}
