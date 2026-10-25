package dev.typetype.server.services

import kotlinx.coroutines.TimeoutCancellationException
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliRiskControlException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

private const val MAX_CAUSE_DEPTH = 16

internal fun Throwable.causeChain(): List<Throwable> {
    val chain = ArrayList<Throwable>()
    var current: Throwable? = this
    while (current != null && chain.size < MAX_CAUSE_DEPTH && chain.none { it === current }) {
        chain += current
        current = current.cause
    }
    return chain
}

internal fun Throwable.isProviderAccessBlock(): Boolean = this is AntiBotException || this is ReCaptchaException

internal fun Throwable.isProviderRateLimit(): Boolean = this is BilibiliRiskControlException

internal fun Throwable.isUpstreamTimeout(): Boolean = when (this) {
    is TimeoutCancellationException,
    is SocketTimeoutException -> true
    is InterruptedIOException -> message == OKHTTP_TIMEOUT_MESSAGE
    else -> false
}

private const val OKHTTP_TIMEOUT_MESSAGE = "timeout"
