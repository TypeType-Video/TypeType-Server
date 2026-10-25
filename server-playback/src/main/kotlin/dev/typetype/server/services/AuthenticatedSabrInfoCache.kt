package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class AuthenticatedSabrInfoCache(
    private val ttl: Duration = Duration.ofMinutes(5),
    maxEntries: Int = 256,
    private val timeoutMs: Long = AuthenticatedSabrPolicy.INFO_TIMEOUT_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val items = BoundedExpiringCache<Key, CachedReady>(
        maxEntries = maxEntries,
        ttl = ttl,
        clock = nowMillis,
    )
    private val inFlight = ConcurrentHashMap<Key, CompletableDeferred<AuthenticatedSabrInfoResult>>()

    suspend fun getOrLoad(
        credentials: YoutubeSessionCredentials,
        videoId: String,
        epoch: Long = 0L,
        loader: suspend () -> AuthenticatedSabrInfoResult,
    ): AuthenticatedSabrInfoResult = getOrLoad(credentials.userId, credentials.fingerprint, videoId, epoch, loader)

    suspend fun getOrLoad(
        userId: String,
        identityFingerprint: String,
        videoId: String,
        epoch: Long = 0L,
        loader: suspend () -> AuthenticatedSabrInfoResult,
    ): AuthenticatedSabrInfoResult {
        val key = Key(userId, identityFingerprint, videoId)
        cached(key, epoch)?.let { return it }
        val pending = CompletableDeferred<AuthenticatedSabrInfoResult>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return awaitExisting(userId, identityFingerprint, videoId, epoch, loader, existing)
        return try {
            val result = withTimeout(timeoutMs) { loader() }
            if (result is AuthenticatedSabrInfoResult.Ready) cache(key, result, epoch)
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
        userId: String,
        identityFingerprint: String,
        videoId: String,
        epoch: Long,
        loader: suspend () -> AuthenticatedSabrInfoResult,
        pending: CompletableDeferred<AuthenticatedSabrInfoResult>,
    ): AuthenticatedSabrInfoResult = try {
        withTimeout(timeoutMs) { pending.await() }
    } catch (error: TimeoutCancellationException) {
        throw error
    } catch (error: CancellationException) {
        if (!currentCoroutineContext().isActive) throw error
        getOrLoad(userId, identityFingerprint, videoId, epoch, loader)
    }

    private fun cached(key: Key, epoch: Long): AuthenticatedSabrInfoResult.Ready? {
        val entry = items.get(key) ?: return null
        if (entry.expiresAtMs > nowMillis() && entry.epoch >= epoch) return entry.result
        items.remove(key)
        return null
    }

    private fun cache(key: Key, result: AuthenticatedSabrInfoResult.Ready, epoch: Long) {
        val response = result.streamResponse
        if (result.prepared.isLive || result.prepared.isLiveContent || response?.isLive == true || response?.isLiveContent == true) return
        val now = nowMillis()
        val maximumExpiry = now + ttl.toMillis()
        val signedDeadline = sequence {
            yield(result.prepared.info.serverAbrStreamingUrl.orEmpty())
            result.streamResponse?.let { response ->
                yield(response.hlsUrl)
                yield(response.dashMpdUrl)
                response.videoStreams.forEach {
                    yield(it.url)
                    yield(it.manifestUrl.orEmpty())
                    yield(it.sabrSessionUrl.orEmpty())
                }
                response.videoOnlyStreams.forEach {
                    yield(it.url)
                    yield(it.manifestUrl.orEmpty())
                    yield(it.sabrSessionUrl.orEmpty())
                }
                response.audioStreams.forEach {
                    yield(it.url)
                    yield(it.manifestUrl.orEmpty())
                    yield(it.sabrSessionUrl.orEmpty())
                }
            }
        }.mapNotNull { it.mediaExpiryEpochMs() }.minOrNull()?.minus(URL_EXPIRY_SAFETY_MS)
        val expiry = signedDeadline?.let { minOf(maximumExpiry, it) } ?: maximumExpiry
        if (expiry - now < MIN_CACHE_TTL_MS) return
        items.put(key, CachedReady(result, expiry, epoch))
    }

    private fun String.mediaExpiryEpochMs(): Long? {
        val url = toHttpUrlOrNull() ?: return null
        return url.queryParameterNames.asSequence()
            .filter { it.lowercase() in MEDIA_EXPIRY_PARAMETERS }
            .flatMap { url.queryParameterValues(it).asSequence() }
            .mapNotNull { value ->
                value?.toLongOrNull()
                    ?.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
                    ?.times(1_000L)
            }
            .minOrNull()
    }

    private data class Key(
        val userId: String,
        val identityFingerprint: String,
        val videoId: String,
    )

    private data class CachedReady(
        val result: AuthenticatedSabrInfoResult.Ready,
        val expiresAtMs: Long,
        val epoch: Long,
    )

    private companion object {
        const val URL_EXPIRY_SAFETY_MS = 300_000L
        const val MIN_CACHE_TTL_MS = 60_000L
        val MEDIA_EXPIRY_PARAMETERS = setOf("deadline", "expire", "expires", "exp", "expiry", "expire_at", "expires_at")
    }
}
