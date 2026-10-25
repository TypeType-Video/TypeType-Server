package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.LiveNotStartException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

object StreamExtractionErrorMapper {
    const val UPSTREAM_FAILURE_CODE = "upstream_failure"
    const val UPSTREAM_TIMEOUT_CODE = "upstream_timeout"
    const val PROVIDER_RATE_LIMITED_CODE = "provider_rate_limited"
    const val PROVIDER_DATA_MISSING_CODE = "provider_data_missing"
    const val MEMBERS_ONLY_FALLBACK = "This video is only available for members"
    const val PAID_CONTENT_FALLBACK = "This video is a paid video"
    const val GEOGRAPHIC_RESTRICTION_CODE = "geographic_restriction"
    const val PROVIDER_ACCESS_BLOCKED_CODE = "provider_access_blocked"
    const val PRIVATE_CONTENT_CODE = "private_content"
    const val GEOGRAPHIC_RESTRICTION_FALLBACK =
        "This video is not available in the server's region. A VPN or another outbound network may help."
    const val PROVIDER_ACCESS_BLOCKED_FALLBACK =
        "The video provider is blocking requests from this TypeType server. Try a VPN or another outbound network for the server, then retry."
    const val PROVIDER_RATE_LIMITED_FALLBACK =
        "The video provider is limiting requests from this TypeType server. Retry in a minute."
    const val UPSTREAM_TIMEOUT_FALLBACK = "The video provider did not answer in time"
    const val PROVIDER_DATA_MISSING_FALLBACK = "The video provider did not return the data needed to play this video"
    const val PRIVATE_CONTENT_FALLBACK = "This video is private or no longer available"
    const val PROVIDER_RATE_LIMIT_RETRY_AFTER_SECONDS = 60L

    fun <T> map(error: Throwable, sourceUrl: String? = null, fallback: String = "Extraction failed"): ExtractionResult<T> {
        val chain = error.causeChain()
        return when {
            chain.any { it.isProviderAccessBlock() } -> ExtractionResult.Failure(
                PROVIDER_ACCESS_BLOCKED_FALLBACK,
                PROVIDER_ACCESS_BLOCKED_CODE,
                ExtractionFailureKind.ProviderAccessBlocked,
            )
            chain.any { it.isProviderRateLimit() } -> ExtractionResult.Failure(
                PROVIDER_RATE_LIMITED_FALLBACK,
                PROVIDER_RATE_LIMITED_CODE,
                ExtractionFailureKind.RateLimited,
                PROVIDER_RATE_LIMIT_RETRY_AFTER_SECONDS,
            )
            else -> chain.firstNotNullOfOrNull(::mapByType) ?: mapUntyped(error, chain, fallback)
        }
    }

    private fun mapByType(error: Throwable): ExtractionResult<Nothing>? = when (error) {
        is NeedLoginException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: MEMBERS_ONLY_FALLBACK,
            "members_only",
        )
        is PaidContentException -> paidContent(error.message)
        is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: PAID_CONTENT_FALLBACK,
            "paid_content",
        )
        is LiveNotStartException -> ExtractionResult.Failure(
            sanitize(error.message) ?: "This live event has not started yet",
            "live_not_started",
            ExtractionFailureKind.LiveEventNotStarted,
        )
        is VideoNotReleaseException -> ExtractionResult.Failure(
            sanitize(error.message) ?: "This premiere has not started yet",
            "scheduled_premiere",
            ExtractionFailureKind.ScheduledPremiere,
        )
        is AgeRestrictedContentException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: "This video is age-restricted",
            "age_restricted",
        )
        is GeographicRestrictionException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: GEOGRAPHIC_RESTRICTION_FALLBACK,
            GEOGRAPHIC_RESTRICTION_CODE,
        )
        is PrivateContentException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: PRIVATE_CONTENT_FALLBACK,
            PRIVATE_CONTENT_CODE,
        )
        is ContentNotAvailableException -> ExtractionResult.Failure(
            sanitize(error.message) ?: "This content is not available",
            "content_unavailable",
            ExtractionFailureKind.ContentUnavailable,
        )
        else -> null
    }

    private fun mapUntyped(error: Throwable, chain: List<Throwable>, fallback: String): ExtractionResult<Nothing> = when {
        chain.any { it.javaClass.name == YOUTUBE_SESSION_REJECTED_EXCEPTION } -> ExtractionResult.Failure(
            sanitize(error.message) ?: fallback,
            "youtube_session_rejected",
            ExtractionFailureKind.YoutubeSessionRejected,
        )
        chain.any { it.isUpstreamTimeout() } -> ExtractionResult.Failure(
            UPSTREAM_TIMEOUT_FALLBACK,
            UPSTREAM_TIMEOUT_CODE,
            ExtractionFailureKind.UpstreamTimeout,
        )
        chain.any { it is NoSuchElementException } -> ExtractionResult.Failure(
            PROVIDER_DATA_MISSING_FALLBACK,
            PROVIDER_DATA_MISSING_CODE,
            ExtractionFailureKind.Unavailable,
        )
        chain.any { it is IllegalArgumentException } -> ExtractionResult.Failure(
            sanitize(error.message) ?: fallback,
            UPSTREAM_FAILURE_CODE,
            ExtractionFailureKind.Unavailable,
        )
        else -> ExtractionResult.Failure(sanitize(error.message) ?: fallback, UPSTREAM_FAILURE_CODE)
    }

    private fun paidContent(message: String?): ExtractionResult.BadRequest {
        val sanitized = sanitize(message)
        return if (sanitized == MEMBERS_ONLY_FALLBACK) {
            ExtractionResult.BadRequest(sanitized, "members_only")
        } else {
            ExtractionResult.BadRequest(sanitized ?: PAID_CONTENT_FALLBACK, "paid_content")
        }
    }

    private fun sanitize(message: String?): String? = ExtractionErrorSanitizer.sanitize(message)

    private const val YOUTUBE_SESSION_REJECTED_EXCEPTION =
        "org.schabi.newpipe.extractor.exceptions.YoutubeSessionRejectedException"
}
