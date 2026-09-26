package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

suspend fun <T> withExtractionRetry(
    attempts: Int = 3,
    initialDelayMs: Long = 250,
    block: suspend () -> T,
): T {
    var delayMs = initialDelayMs
    var lastError: Throwable? = null
    repeat(attempts) { index ->
        runCatching { return block() }
            .onFailure {
                if (!it.isRetriableExtractionError() || index == attempts - 1) throw it
                lastError = it
            }
        delay(delayMs)
        delayMs = (delayMs * 2).coerceAtMost(1500)
    }
    throw lastError ?: IllegalStateException("Extraction retry failed")
}

private fun Throwable.isRetriableExtractionError(): Boolean = when (this) {
    is CancellationException,
    is ContentNotAvailableException,
    is AgeRestrictedContentException,
    is GeographicRestrictionException,
    is PaidContentException,
    is PrivateContentException,
    is NeedLoginException,
    is AntiBotException,
    is ReCaptchaException,
    is IllegalArgumentException -> false
    else -> true
}
