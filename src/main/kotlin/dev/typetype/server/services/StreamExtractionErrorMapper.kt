package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

internal object StreamExtractionErrorMapper {
    const val MEMBERS_ONLY_FALLBACK = "This video is only available for members"
    const val PAID_CONTENT_FALLBACK = "This video is a paid video"
    const val GEOGRAPHIC_RESTRICTION_CODE = "geographic_restriction"
    const val PROVIDER_ACCESS_BLOCKED_CODE = "provider_access_blocked"
    const val PRIVATE_CONTENT_CODE = "private_content"
    const val GEOGRAPHIC_RESTRICTION_FALLBACK =
        "This video is not available in the server's region. A VPN or another outbound network may help."
    const val PROVIDER_ACCESS_BLOCKED_FALLBACK =
        "The video provider is blocking requests from this TypeType server. Try a VPN or another outbound network for the server, then retry."
    const val PRIVATE_CONTENT_FALLBACK = "This video is private or no longer available"

    fun <T> map(error: Throwable, sourceUrl: String? = null, fallback: String = "Extraction failed"): ExtractionResult<T> =
        mapByType(error, fallback)

    private fun <T> mapByType(error: Throwable, fallback: String): ExtractionResult<T> = when (error) {
        is NeedLoginException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: MEMBERS_ONLY_FALLBACK,
            "members_only",
        )
        is PaidContentException -> paidContent(error.message)
        is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: PAID_CONTENT_FALLBACK,
            "paid_content",
        )
        is VideoNotReleaseException -> ExtractionResult.Failure(
            sanitize(error.message) ?: "This premiere has not started yet",
            "scheduled_premiere",
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
        is AntiBotException,
        is ReCaptchaException -> ExtractionResult.Failure(
            PROVIDER_ACCESS_BLOCKED_FALLBACK,
            PROVIDER_ACCESS_BLOCKED_CODE,
        )
        else -> ExtractionResult.Failure(
            sanitize(error.message) ?: fallback,
            kind = if (error.isYoutubeSessionRejected()) {
                ExtractionFailureKind.YoutubeSessionRejected
            } else {
                ExtractionFailureKind.Unknown
            },
        )
    }

    private fun paidContent(message: String?): ExtractionResult.BadRequest {
        val sanitized = sanitize(message)
        return if (sanitized == MEMBERS_ONLY_FALLBACK) {
            ExtractionResult.BadRequest(sanitized, "members_only")
        } else {
            ExtractionResult.BadRequest(sanitized ?: PAID_CONTENT_FALLBACK, "paid_content")
        }
    }

    private fun Throwable.isYoutubeSessionRejected(): Boolean =
        generateSequence(this) { it.cause }
            .any { it.javaClass.name == YOUTUBE_SESSION_REJECTED_EXCEPTION }

    private fun sanitize(message: String?): String? = ExtractionErrorSanitizer.sanitize(message)

    private const val YOUTUBE_SESSION_REJECTED_EXCEPTION =
        "org.schabi.newpipe.extractor.exceptions.YoutubeSessionRejectedException"
}
