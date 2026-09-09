package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal fun parseNicoCookie(fragment: String): String? {
    val decoded = URLDecoder.decode(fragment, StandardCharsets.UTF_8)
    val cookieParam = decoded.split("&")
        .firstOrNull { it.startsWith("cookie=") }
        ?.removePrefix("cookie=") ?: return null
    val eqIdx = cookieParam.indexOf('=')
    if (eqIdx < 0 || cookieParam.substring(0, eqIdx) != "domand_bid") return null
    return cookieParam.substring(eqIdx + 1)
}

internal fun rewriteNicoManifest(manifest: String, baseUrl: String, domandBid: String? = null, proxyPath: String = "nicovideo"): String {
    val base = URI(baseUrl)
    val uriAttr = Regex("""URI="([^"]+)"""")
    val bidSuffix = if (domandBid != null) "&domand_bid=${URLEncoder.encode(domandBid, StandardCharsets.UTF_8)}" else ""
    fun toProxy(url: String) = "$proxyPath?url=" + URLEncoder.encode(
        if (url.startsWith("http")) url else base.resolve(url).toString(), StandardCharsets.UTF_8
    ) + bidSuffix
    return manifest.lines().joinToString("\n") { line ->
        val t = line.trim()
        when {
            t.isBlank() -> line
            t.startsWith("#") -> uriAttr.replace(t) { """URI="${toProxy(it.groupValues[1])}"""" }
            else -> toProxy(t)
        }
    }
}

class NicoVideoProxyService(
    client: OkHttpClient = defaultNicoProxyClient(),
    private val mediaHandleService: ProviderMediaHandleService? = null,
) {
    private val executor = ProxyHttpExecutor(client)

    companion object {
        private fun defaultNicoProxyClient() = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    }

    suspend fun fetchManifest(rawUrl: String, domandBid: String? = null): ExtractionResult<ProxyResponse> =
        withContext(Dispatchers.IO) {
            val hashIdx = rawUrl.indexOf('#')
            val manifestUrl = if (hashIdx >= 0) rawUrl.substring(0, hashIdx) else rawUrl
            val fragment = if (hashIdx >= 0) rawUrl.substring(hashIdx + 1) else ""
            val resolvedBid = domandBid ?: if (fragment.isNotBlank()) parseNicoCookie(fragment) else null
            validateProxyUrl(manifestUrl)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val builder = Request.Builder()
                    .url(manifestUrl)
                    .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
                if (resolvedBid != null) builder.header("Cookie", "domand_bid=$resolvedBid")
                executor.execute(builder.build())
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val text = body.string()
                        response.close()
                        val rewritten = if (mediaHandleService == null) {
                            rewriteNicoManifest(text, manifestUrl, resolvedBid)
                        } else {
                            rewriteProviderHlsManifest(text, manifestUrl) { target ->
                                mediaHandleService.relativeManifestPath(
                                    mediaHandleService.createPath(target, resolvedBid),
                                )
                            }
                        }
                        ExtractionResult.Success(ProxyResponse(
                            status = 200,
                            contentType = "application/vnd.apple.mpegurl",
                            contentLength = null,
                            contentRange = null,
                            acceptRanges = null,
                            stream = ByteArrayInputStream(rewritten.toByteArray(StandardCharsets.UTF_8)),
                            close = {},
                        ))
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "NicoNico proxy fetch failed") }
            )
        }

    suspend fun fetchSegment(url: String, rangeHeader: String?, domandBid: String? = null): ExtractionResult<ProxyResponse> =
        withContext(Dispatchers.IO) {
            validateProxyUrl(url)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
                if (rangeHeader != null) builder.header("Range", rangeHeader)
                if (domandBid != null) builder.header("Cookie", "domand_bid=$domandBid")
                executor.execute(builder.build())
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful && response.code != 206) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        ExtractionResult.Success(ProxyResponse(
                            status = response.code,
                            contentType = response.header("Content-Type") ?: "application/octet-stream",
                            contentLength = response.header("Content-Length")?.toLongOrNull(),
                            contentRange = response.header("Content-Range"),
                            acceptRanges = response.header("Accept-Ranges"),
                            stream = body.byteStream(),
                            close = response::close,
                            cacheControl = response.header("Cache-Control"),
                        ))
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "NicoNico segment fetch failed") }
            )
        }
}
