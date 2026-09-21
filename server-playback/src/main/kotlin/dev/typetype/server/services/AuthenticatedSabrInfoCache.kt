package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class AuthenticatedSabrInfoCache(
    ttl: Duration = Duration.ofMinutes(5),
    maxEntries: Int = 256,
    private val timeoutMs: Long = AuthenticatedSabrPolicy.INFO_TIMEOUT_MS,
) {
    private val items = BoundedExpiringCache<Key, SabrPreparedInfo>(
        maxEntries = maxEntries,
        ttl = ttl,
    )
    private val inFlight = ConcurrentHashMap<Key, CompletableDeferred<AuthenticatedSabrInfoResult>>()

    suspend fun getOrLoad(
        credentials: YoutubeSessionCredentials,
        videoId: String,
        loader: suspend () -> AuthenticatedSabrInfoResult,
    ): AuthenticatedSabrInfoResult {
        val key = Key(credentials.userId, credentials.fingerprint, videoId)
        items.get(key)?.let { return AuthenticatedSabrInfoResult.Ready(it) }
        val pending = CompletableDeferred<AuthenticatedSabrInfoResult>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return awaitExisting(credentials, videoId, loader, existing)
        return try {
            val result = withTimeout(timeoutMs) { loader() }
            if (result is AuthenticatedSabrInfoResult.Ready) items.put(key, result.prepared)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private suspend fun awaitExisting(
        credentials: YoutubeSessionCredentials,
        videoId: String,
        loader: suspend () -> AuthenticatedSabrInfoResult,
        pending: CompletableDeferred<AuthenticatedSabrInfoResult>,
    ): AuthenticatedSabrInfoResult = try {
        withTimeout(timeoutMs) { pending.await() }
    } catch (error: TimeoutCancellationException) {
        throw error
    } catch (error: CancellationException) {
        if (!currentCoroutineContext().isActive) throw error
        getOrLoad(credentials, videoId, loader)
    }

    private data class Key(
        val userId: String,
        val credentialFingerprint: String,
        val videoId: String,
    )
}
