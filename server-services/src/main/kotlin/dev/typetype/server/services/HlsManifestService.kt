package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class HlsManifestService(
    private val streamService: StreamService,
    httpClient: OkHttpClient,
    cache: CacheService? = null,
    private val signManifestUrl: ((String) -> String)? = null,
    private val attestedYoutubeHls: suspend (String) -> String? = { null },
    private val providerMediaHandleService: ProviderMediaHandleService? = null,
) {
    private val proxyHttp = ProxyHttpExecutor(httpClient)
    private val manifestCache = cache?.let(::HlsManifestCache)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ExtractionResult<String>>>()

    suspend fun hlsManifest(url: String, signManifestLinks: Boolean = false): ExtractionResult<String> {
        val manifestUrl = if (isManifestUrl(url)) {
            url
        } else {
            when (val resolved = resolveHlsUrl(url)) {
                is ExtractionResult.Success -> resolved.data
                is ExtractionResult.BadRequest -> return resolved
                is ExtractionResult.Failure -> return resolved
            }
        }
        return cachedOrFetch(manifestUrl, signManifestLinks)
    }

    suspend fun hlsManifestFromStreamInfo(
        result: ExtractionResult<StreamResponse>,
        signManifestLinks: Boolean = false,
    ): ExtractionResult<String> {
        val manifestUrl = when (val resolved = resolveHlsUrl(result, allowAttestedYoutubeHls = true)) {
            is ExtractionResult.Success -> resolved.data
            is ExtractionResult.BadRequest -> return resolved
            is ExtractionResult.Failure -> return resolved
        }
        return cachedOrFetch(manifestUrl, signManifestLinks)
    }

    private suspend fun cachedOrFetch(manifestUrl: String, signManifestLinks: Boolean): ExtractionResult<String> {
        val cache = manifestCache.takeUnless {
            isProviderManifestUrl(manifestUrl) || isYoutubeLiveManifest(manifestUrl)
        }
        val cacheKey = if (signManifestLinks) "signed:$manifestUrl" else manifestUrl
        cache?.get(cacheKey)?.let { return ExtractionResult.Success(it) }
        val pending = CompletableDeferred<ExtractionResult<String>>()
        val existing = inFlight.putIfAbsent(cacheKey, pending)
        if (existing != null) return existing.await()
        return try {
            cache?.get(cacheKey)?.let {
                val result = ExtractionResult.Success(it)
                pending.complete(result)
                return result
            }
            val result = fetchAndRewrite(manifestUrl, signManifestLinks)
            if (result is ExtractionResult.Success) cache?.set(cacheKey, result.data)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(cacheKey, pending)
        }
    }

    private suspend fun resolveHlsUrl(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        return resolveHlsUrl(result, allowAttestedYoutubeHls = isYoutubeUrl(videoUrl))
    }

    private suspend fun resolveHlsUrl(
        result: ExtractionResult<StreamResponse>,
        allowAttestedYoutubeHls: Boolean = false,
    ): ExtractionResult<String> {
        val success = when (result) {
            is ExtractionResult.BadRequest -> return result
            is ExtractionResult.Failure -> return ExtractionResult.Failure(result.message, result.code, result.kind)
            is ExtractionResult.Success -> result
        }
        if (allowAttestedYoutubeHls && success.data.isLive) {
            attestedYoutubeHls(success.data.id)?.let { return ExtractionResult.Success(it) }
        }
        val hls = success.data.hlsUrl
        return if (hls.isNotBlank()) {
            ExtractionResult.Success(hls)
        } else {
            ExtractionResult.Failure("No HLS stream available for this video", "no_playable_streams")
        }
    }

    private suspend fun fetchAndRewrite(manifestUrl: String, signManifestLinks: Boolean): ExtractionResult<String> =
        withContext(Dispatchers.IO) {
            val hashIndex = manifestUrl.indexOf('#')
            val fetchUrl = if (hashIndex >= 0) manifestUrl.substring(0, hashIndex) else manifestUrl
            val fragment = if (hashIndex >= 0) manifestUrl.substring(hashIndex + 1) else ""
            val domandBid = fragment.takeIf { it.isNotBlank() }?.let(::parseNicoCookie)
            validateProxyUrl(fetchUrl)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val request = Request.Builder()
                    .url(fetchUrl)
                    .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
                    .apply { if (domandBid != null) header("Cookie", "domand_bid=$domandBid") }
                    .build()
                proxyHttp.execute(request)
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val text = body.string()
                        response.close()
                        val rewritten = if (providerMediaHandleService != null && isNicoNicoManifest(fetchUrl)) {
                            rewriteProviderHlsManifest(text, fetchUrl) { target ->
                                providerMediaHandleService.relativeManifestPath(
                                    providerMediaHandleService.createPath(target, domandBid),
                                )
                            }
                        } else if (providerMediaHandleService != null && isBilibiliManifest(fetchUrl)) {
                            rewriteProviderHlsManifest(text, fetchUrl) { target ->
                                providerMediaHandleService.relativeManifestPath(
                                    providerMediaHandleService.createPath(target),
                                )
                            }
                        } else if (isNicoNicoManifest(fetchUrl)) {
                            rewriteNicoManifest(text, fetchUrl, domandBid, "../proxy")
                        } else {
                            rewriteYouTubeHlsManifest(text) { target ->
                                if (signManifestLinks) signManifestUrl?.invoke(target) ?: toHlsProxyUrl(target)
                                else toHlsProxyUrl(target)
                            }
                        }
                        ExtractionResult.Success(rewritten)
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "HLS manifest fetch failed") }
            )
        }

    private fun isNicoNicoManifest(url: String): Boolean =
        runCatching { providerForProxyHost(URI(url).host.orEmpty()) == ProxyProvider.NICONICO }
            .getOrDefault(false)

    private fun isBilibiliManifest(url: String): Boolean =
        runCatching { providerForProxyHost(URI(url).host.orEmpty()) == ProxyProvider.BILIBILI }
            .getOrDefault(false)

    private fun isProviderManifestUrl(url: String): Boolean =
        isNicoNicoManifest(url) || isBilibiliManifest(url)

    private fun isYoutubeLiveManifest(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        val isYoutubeHost = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
        isYoutubeHost && uri.path.orEmpty().split('/').any { it == "yt_live_broadcast" }
    }.getOrDefault(false)
}
