package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.transientAwareFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliRiskControlException
import java.net.SocketTimeoutException

class TransientExtractionFailureTest {
    @Test
    fun `keeps the typed kind of transient provider failures`() {
        assertEquals(
            ExtractionFailureKind.ProviderAccessBlocked,
            transientAwareFailure(ExtractionException("x", AntiBotException("bot")), "Search failed").kind,
        )
        assertEquals(
            ExtractionFailureKind.RateLimited,
            transientAwareFailure(BilibiliRiskControlException("risk", -412), "Search failed").kind,
        )
        assertEquals(
            ExtractionFailureKind.UpstreamTimeout,
            transientAwareFailure(SocketTimeoutException("Read timed out"), "Search failed").kind,
        )
    }

    @Test
    fun `keeps the previous answer for every other failure`() {
        assertEquals(
            ExtractionResult.Failure("Only in Japan"),
            transientAwareFailure(GeographicRestrictionException("Only in Japan"), "Search failed"),
        )
        assertEquals(ExtractionResult.Failure("Search failed"), transientAwareFailure(IllegalStateException(), "Search failed"))
    }
}
