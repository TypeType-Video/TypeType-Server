package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.coroutines.sync.Semaphore
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

val GOOGLEVIDEO_URL_REGEX = Regex("""https://[a-z0-9.\-]+\.googlevideo\.com/\S+""")
private val CPN_TRACKING_PARAM_REGEX = Regex("[&?]cpn=[^&]*")
private val PPPID_TRACKING_PARAM_REGEX = Regex("[&?]pppid=[^&]*")

fun stripTrackingParams(url: String): String =
    url.replace(CPN_TRACKING_PARAM_REGEX, "")
        .replace(PPPID_TRACKING_PARAM_REGEX, "")

fun rewriteHlsManifest(manifest: String): String =
    manifest.replace(GOOGLEVIDEO_URL_REGEX) { match ->
        "/proxy?url=" + URLEncoder.encode(match.value, StandardCharsets.UTF_8)
    }

class OkHttpProxyService(
    private val client: OkHttpClient,
    private val mediaHandleService: ProviderMediaHandleService? = null,
) : ProxyService, ProviderMediaAwareProxyService {
    private val executor = ProxyHttpExecutor(client)
    private val boundedExecutor = ProxyHttpExecutor(
        client.newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build(),
    )
    private val mirrorPermits = Semaphore(MAX_CONCURRENT_MIRROR_PROBES)
    private val mirrorExecutor = ProxyHttpExecutor(
        client.newBuilder()
            .connectTimeout(MIRROR_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(MIRROR_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build(),
    )
    private val bilibiliProxyClient = client.newBuilder()
        .connectTimeout(BILIBILI_PROXY_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BILIBILI_PROXY_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val bilibiliProxyExecutor = ProxyHttpExecutor(bilibiliProxyClient)

    override suspend fun pipe(url: String, rangeHeader: String?, domandBid: String?): ExtractionResult<ProxyResponse> =
        pipeInternal(url, rangeHeader, domandBid, providerMediaManifest = false)

    override suspend fun pipe(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        cdnRegion: String?,
        enablePrefetch: Boolean,
    ): ExtractionResult<ProxyResponse> =
        pipeInternal(url, rangeHeader, domandBid, providerMediaManifest = false, cdnRegion = cdnRegion, enablePrefetch = enablePrefetch)

    override suspend fun pipeProviderMedia(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
    ): ExtractionResult<ProxyResponse> = pipeInternal(url, rangeHeader, domandBid, providerMediaManifest = true)

    override suspend fun pipeProviderMediaWithAlternates(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        alternateUrls: List<String>,
    ): ExtractionResult<ProxyResponse> =
        pipeInternal(url, rangeHeader, domandBid, providerMediaManifest = true, alternateUrls = alternateUrls)

    override suspend fun pipeProviderMediaWithAlternates(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        alternateUrls: List<String>,
        cdnRegion: String?,
        enablePrefetch: Boolean,
    ): ExtractionResult<ProxyResponse> =
        pipeInternal(
            url = url,
            rangeHeader = rangeHeader,
            domandBid = domandBid,
            providerMediaManifest = true,
            alternateUrls = alternateUrls,
            cdnRegion = cdnRegion,
            enablePrefetch = enablePrefetch,
        )

    private suspend fun pipeInternal(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        providerMediaManifest: Boolean,
        alternateUrls: List<String> = emptyList(),
        cdnRegion: String? = null,
        enablePrefetch: Boolean = true,
    ): ExtractionResult<ProxyResponse> {
        val requestContext = currentCoroutineContext()
        return withContext(Dispatchers.IO) {
            val hashIdx = url.indexOf('#')
            val fetchUrl = if (hashIdx >= 0) url.substring(0, hashIdx) else url
            val fragment = if (hashIdx >= 0) url.substring(hashIdx + 1) else ""
            val resolvedDomandBid = domandBid ?: if (fragment.isNotBlank()) parseNicoCookie(fragment) else null
            validateProxyUrl(fetchUrl)?.let { return@withContext ExtractionResult.BadRequest(it) }
            val requestExecutor = if (rangeHeader == null) boundedExecutor else executor
            runCatching {
                val cleanUrl = stripTrackingParams(fetchUrl)
                val bilibili = isBilibili(cleanUrl)
                val builder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", if (bilibili) BILIBILI_USER_AGENT else BROWSER_USER_AGENT)
                if (bilibili) {
                    builder.header("Referer", BILIBILI_REFERER)
                    builder.header("Accept", ACCEPT_ANY)
                }
                if (resolvedDomandBid != null && isNicoNico(cleanUrl)) builder.header("Cookie", "domand_bid=$resolvedDomandBid")
                if (rangeHeader != null) builder.header("Range", rangeHeader)
                val request = builder.build()
                if (bilibili && rangeHeader != null) {
                    val safeAlternates = if (providerMediaManifest) {
                        validateBilibiliAlternates(cleanUrl, alternateUrls)
                    } else {
                        emptyList()
                    }
                    return@withContext readBilibiliRangeWithRetry(
                        bilibiliProxyExecutor::execute,
                        request,
                        requestContext::ensureActive,
                        safeAlternates,
                        mirrorExecutor::execute,
                        mirrorPermits,
                        bilibiliProxyClient.readTimeoutMillis,
                        cdnRegion = cdnRegion,
                        enablePrefetch = enablePrefetch,
                    )
                }
                requestExecutor.execute(request)
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    val youtubeThumbnailFallback = acceptsYoutubeThumbnailFallback(
                        fetchUrl,
                        response.code,
                        response.header("Content-Type"),
                    )
                    if (!response.isSuccessful && response.code != 206 && !youtubeThumbnailFallback) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val statusCode = if (youtubeThumbnailFallback) 200 else response.code
                        val contentType = response.header("Content-Type") ?: "application/octet-stream"
                        val contentRange = response.header("Content-Range")
                        val acceptRanges = response.header("Accept-Ranges")
                        val cacheControl = response.header("Cache-Control")
                        val contentLength = response.header("Content-Length")?.toLongOrNull()
                        val cleanFetchUrl = stripTrackingParams(fetchUrl)
                        if (isHls(contentType, cleanFetchUrl)) {
                            val rewritten = if (providerMediaManifest && mediaHandleService != null && isNicoNico(cleanFetchUrl)) {
                                rewriteProviderHlsManifest(body.string(), cleanFetchUrl) { target ->
                                    mediaHandleService.relativeManifestPath(
                                        mediaHandleService.createPath(target, resolvedDomandBid),
                                    )
                                }
                            } else if (providerMediaManifest && mediaHandleService != null && isBilibili(cleanFetchUrl)) {
                                rewriteProviderHlsManifest(body.string(), cleanFetchUrl) { target ->
                                    mediaHandleService.relativeManifestPath(mediaHandleService.createPath(target))
                                }
                            } else if (isNicoNico(cleanFetchUrl)) {
                                rewriteNicoManifest(body.string(), cleanFetchUrl, resolvedDomandBid, PROXY_PATH)
                            } else {
                                rewriteHlsManifest(body.string())
                            }
                            response.close()
                            ExtractionResult.Success(ProxyResponse(
                                status = statusCode,
                                contentType = contentType,
                                contentLength = null,
                                contentRange = null,
                                acceptRanges = null,
                                cacheControl = cacheControl,
                                stream = ByteArrayInputStream(rewritten.toByteArray(StandardCharsets.UTF_8)),
                                close = {},
                            ))
                        } else {
                            ExtractionResult.Success(ProxyResponse(
                                status = statusCode,
                                contentType = contentType,
                                contentLength = contentLength,
                                contentRange = contentRange,
                                acceptRanges = acceptRanges,
                                cacheControl = cacheControl,
                                stream = body.byteStream(),
                                close = response::close,
                            ))
                        }
                    }
                },
                onFailure = {
                    requestContext.ensureActive()
                    ExtractionResult.Failure(it.message ?: "Proxy fetch failed")
                }
            )
        }
    }

    private fun isBilibili(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrElse { "" }
        return host.contains("bilibili") || host.contains("bilivideo") || host.endsWith("hdslb.com") || host.contains("akamaized")
    }

    private fun validateBilibiliAlternates(primaryUrl: String, alternateUrls: List<String>): List<String> {
        val primary = runCatching { requireProxyTarget(primaryUrl) }.getOrNull() ?: return emptyList()
        if (primary.provider != ProxyProvider.BILIBILI) return emptyList()
        return alternateUrls.asSequence().take(MAX_ALTERNATE_URLS).mapNotNull { value ->
            runCatching { requireProxyTarget(stripTrackingParams(value)) }.getOrNull()?.takeIf {
                it.provider == ProxyProvider.BILIBILI && it.url.encodedPath == primary.url.encodedPath
            }?.url?.toString()
        }.filter { it != primary.url.toString() }.distinct().toList()
    }

    private fun isNicoNico(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrElse { "" }
        return providerForProxyHost(host) == ProxyProvider.NICONICO
    }

    private fun isHls(contentType: String, url: String): Boolean =
        contentType.contains("mpegurl", ignoreCase = true) || runCatching { URI(url).path?.endsWith(".m3u8", ignoreCase = true) == true }.getOrDefault(false)

    companion object {
        private const val MAX_CONCURRENT_MIRROR_PROBES = 8
        private const val MAX_ALTERNATE_URLS = 4
        private const val MIRROR_CONNECT_TIMEOUT_SECONDS = 2L
        private const val MIRROR_READ_TIMEOUT_SECONDS = 2L
        private const val BILIBILI_PROXY_CONNECT_TIMEOUT_SECONDS = 3L
        private const val BILIBILI_PROXY_READ_TIMEOUT_SECONDS = 8L
        private const val BILIBILI_REFERER = "https://www.bilibili.com"
        private const val ACCEPT_ANY = "*/*"
        private const val PROXY_PATH = "proxy"
        const val BILIBILI_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}

fun acceptsYoutubeThumbnailFallback(url: String, status: Int, contentType: String?): Boolean {
    val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
    return status == 404 && host.endsWith("ytimg.com") && contentType?.startsWith("image/") == true
}
