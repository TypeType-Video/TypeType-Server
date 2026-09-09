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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal val GOOGLEVIDEO_URL_REGEX = Regex("""https://[a-z0-9.\-]+\.googlevideo\.com/\S+""")

internal fun stripTrackingParams(url: String): String =
    url.replace(Regex("[&?]cpn=[^&]*"), "")
        .replace(Regex("[&?]pppid=[^&]*"), "")

internal fun rewriteHlsManifest(manifest: String): String =
    manifest.replace(GOOGLEVIDEO_URL_REGEX) { match ->
        "/proxy?url=" + URLEncoder.encode(match.value, StandardCharsets.UTF_8)
    }

class OkHttpProxyService(client: OkHttpClient) : ProxyService {
    private val executor = ProxyHttpExecutor(client)

    override suspend fun pipe(url: String, rangeHeader: String?, domandBid: String?): ExtractionResult<ProxyResponse> {
        val requestContext = currentCoroutineContext()
        return withContext(Dispatchers.IO) {
            val hashIdx = url.indexOf('#')
            val fetchUrl = if (hashIdx >= 0) url.substring(0, hashIdx) else url
            val fragment = if (hashIdx >= 0) url.substring(hashIdx + 1) else ""
            val resolvedDomandBid = domandBid ?: if (fragment.isNotBlank()) parseNicoCookie(fragment) else null
            validateProxyUrl(fetchUrl)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val cleanUrl = stripTrackingParams(fetchUrl)
                val bilibili = isBilibili(cleanUrl)
                val builder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", if (bilibili) BILIBILI_USER_AGENT else BROWSER_USER_AGENT)
                if (bilibili) {
                    builder.header("Referer", BILIBILI_REFERER)
                    builder.header("Accept", ACCEPT_ANY)
                    if (rangeHeader != null) builder.header("Connection", "close")
                }
                if (resolvedDomandBid != null && isNicoNico(cleanUrl)) builder.header("Cookie", "domand_bid=$resolvedDomandBid")
                if (rangeHeader != null) builder.header("Range", rangeHeader)
                val request = builder.build()
                if (bilibili && rangeHeader != null) {
                    return@withContext readBilibiliRangeWithRetry(executor::execute, request, requestContext::ensureActive)
                }
                executor.execute(request)
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
                        if (isHls(contentType)) {
                            val rewritten = if (isNicoNico(stripTrackingParams(fetchUrl))) {
                                rewriteNicoManifest(body.string(), stripTrackingParams(fetchUrl), resolvedDomandBid, PROXY_PATH)
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

    private fun isNicoNico(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrElse { "" }
        return host.endsWith("nicovideo.jp")
    }

    private fun isHls(contentType: String): Boolean =
        contentType.contains("mpegurl", ignoreCase = true)

    companion object {
        private const val BILIBILI_REFERER = "https://www.bilibili.com"
        private const val ACCEPT_ANY = "*/*"
        private const val PROXY_PATH = "proxy"
        const val BILIBILI_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}

internal fun acceptsYoutubeThumbnailFallback(url: String, status: Int, contentType: String?): Boolean {
    val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
    return status == 404 && host.endsWith("ytimg.com") && contentType?.startsWith("image/") == true
}
