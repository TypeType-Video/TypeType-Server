package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest

internal suspend fun fetchSabrProbeSegment(
    store: SabrSessionStore,
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    timeoutMs: Long,
): SabrProbeFetchResult {
    val startedAt = System.nanoTime()
    val result = withTimeoutOrNull(timeoutMs) {
        try {
            Result.success(store.fetchSegment(holder, request))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure<SabrMediaSegment?>(error)
        }
    }
    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
    if (result == null) {
        return SabrProbeFetchResult(
            segment = null,
            elapsedMs = elapsedMs,
            timedOut = true,
            error = null,
        )
    }
    return result.fold(
        onSuccess = {
            SabrProbeFetchResult(segment = it, elapsedMs = elapsedMs, timedOut = false, error = null)
        },
        onFailure = {
            SabrProbeFetchResult(segment = null, elapsedMs = elapsedMs, timedOut = false, error = it)
        },
    )
}
