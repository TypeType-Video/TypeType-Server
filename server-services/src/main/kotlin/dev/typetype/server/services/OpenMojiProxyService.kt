package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.util.concurrent.TimeUnit

class OpenMojiProxyService(
    private val cache: CacheService,
    private val client: OkHttpClient = defaultOpenMojiClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val localCache = BoundedExpiringCache<String, ByteArray>(
        maxEntries = LOCAL_CACHE_MAX_ENTRIES,
        maxWeight = LOCAL_CACHE_MAX_BYTES,
        ttl = Duration.ofMinutes(10),
        weigher = { it.size.toLong() },
        clock = clock,
    )
    private val failedUntilByCode = BoundedExpiringCache<String, Unit>(
        maxEntries = COOLDOWN_MAX_ENTRIES,
        ttl = Duration.ofMillis(FAILURE_COOLDOWN_MS),
        clock = clock,
    )
    private val notFoundUntilByCode = BoundedExpiringCache<String, Unit>(
        maxEntries = COOLDOWN_MAX_ENTRIES,
        ttl = Duration.ofMillis(NOT_FOUND_CACHE_MS),
        clock = clock,
    )

    suspend fun getSvg(code: String): ByteArray? {
        val key = cacheKey(code)
        localCache.get(code)?.let { return it }
        if (notFoundUntilByCode.get(code) != null) return null
        if (failedUntilByCode.get(code) != null) return null
        runCatching { cache.get(key) }.getOrNull()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
            localCache.put(code, bytes)
            return bytes
        }
        return when (val fetched = fetch(code)) {
            is FetchResult.Success -> {
                failedUntilByCode.remove(code)
                notFoundUntilByCode.remove(code)
                localCache.put(code, fetched.bytes)
                runCatching { cache.set(key, fetched.bytes.toString(Charsets.UTF_8), SVG_CACHE_TTL_SECONDS) }
                fetched.bytes
            }
            FetchResult.NotFound -> {
                failedUntilByCode.remove(code)
                notFoundUntilByCode.put(code, Unit)
                null
            }
            FetchResult.Failed -> {
                failedUntilByCode.put(code, Unit)
                null
            }
        }
    }

    private suspend fun fetch(code: String): FetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(openMojiCdnUrl(code)).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@use FetchResult.NotFound
                if (!response.isSuccessful) return@use FetchResult.Failed
                val bytes = response.body.bytes()
                FetchResult.Success(bytes)
            }
        }.getOrElse { FetchResult.Failed }
    }

    private fun cacheKey(code: String): String = "openmoji:$code"

    private fun openMojiCdnUrl(code: String): String = "$OPENMOJI_CDN_BASE/$code.svg"

    companion object {
        private const val OPENMOJI_CDN_BASE = "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg"
        private const val SVG_CACHE_TTL_SECONDS = 604800L
        private const val FAILURE_COOLDOWN_MS = 15000L
        private const val NOT_FOUND_CACHE_MS = 300000L
        private const val LOCAL_CACHE_MAX_ENTRIES = 256
        private const val LOCAL_CACHE_MAX_BYTES = 16L * 1024 * 1024
        private const val COOLDOWN_MAX_ENTRIES = 1024
    }

    private sealed interface FetchResult {
        data class Success(val bytes: ByteArray) : FetchResult
        data object NotFound : FetchResult
        data object Failed : FetchResult
    }

}

private fun defaultOpenMojiClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .callTimeout(5, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()
