package dev.typetype.server.downloader

import okhttp3.Callback
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.downloader.StreamingResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor(
    private val client: OkHttpClient,
    private val streamingClient: OkHttpClient,
) : Downloader() {

    companion object {
        fun instance(proxySelector: ProxySelector? = null): OkHttpDownloader =
            create(STREAMING_READ_TIMEOUT_MS, proxySelector)

        internal fun create(
            streamingReadTimeoutMs: Long,
            proxySelector: ProxySelector? = null,
        ): OkHttpDownloader {
            val clientBuilder = OkHttpClient.Builder()
                .dispatcher(sharedDispatcher())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            if (oneRequestPerConnection()) {
                clientBuilder.protocols(listOf(Protocol.HTTP_1_1))
                clientBuilder.connectionPool(ConnectionPool(simultaneousPerHost(), 2, TimeUnit.MINUTES))
            }
            proxySelector?.let(clientBuilder::proxySelector)
            if (ExtractorRequestLog.enabled) clientBuilder.addInterceptor(ExtractorRequestLog)
            val client = clientBuilder.build()
            val streamingClient = client.newBuilder()
                .readTimeout(streamingReadTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
            return OkHttpDownloader(client, streamingClient)
        }

        /**
         * Requests on their way to one host at once. The library's own 5 is too few: a video
         * sends three together, and the others waited until the extractor gave up on them.
         */
        internal fun simultaneousPerHost(setting: String? = System.getenv(SIMULTANEOUS_ENV)): Int =
            setting?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_SIMULTANEOUS_PER_HOST

        /**
         * "1": one request per connection (HTTP/1.1). On the single connection HTTP/2 shares,
         * the requests of a crowd stopped being answered for minutes at a time.
         */
        internal fun oneRequestPerConnection(setting: String? = System.getenv(HTTP_VERSION_ENV)): Boolean =
            setting?.trim() == "1"

        private fun sharedDispatcher(): Dispatcher = Dispatcher().apply {
            maxRequestsPerHost = simultaneousPerHost()
            maxRequests = maxOf(maxRequests, maxRequestsPerHost * 4)
        }

        /**
         * Called when the provider answers "too many requests". The extractor often swallows
         * that error and carries on with a poorer result, so this is the only place it shows.
         */
        @Volatile
        var onProviderRefusal: (() -> Unit)? = null

        private val logger = LoggerFactory.getLogger(OkHttpDownloader::class.java)

        /**
         * The text of a subtitle track is never asked for from here. The extractor downloads it
         * for every video it opens, only to tidy the list of tracks, and the provider guards
         * that address far more closely than any other: a few requests get the whole address
         * of the instance refused. Opening a video does not need it.
         */
        internal fun isSubtitleText(url: String): Boolean = SUBTITLE_TEXT_PATH in url

        private fun refuseSubtitleText(url: String) {
            if (isSubtitleText(url)) throw java.io.IOException("Subtitle text is not fetched by the extractor")
        }

        private const val SUBTITLE_TEXT_PATH = "/api/timedtext"
        private const val HTTP_VERSION_ENV = "EXTRACTOR_HTTP_VERSION"
        private const val SIMULTANEOUS_ENV = "EXTRACTOR_SIMULTANEOUS_REQUESTS_PER_HOST"
        private const val DEFAULT_SIMULTANEOUS_PER_HOST = 64
        private const val STREAMING_READ_TIMEOUT_MS = 30_000L
        private const val YOUTUBE_AUTH_USER_HEADER = "X-Goog-AuthUser"
    }

    override fun execute(request: ExtractorRequest): Response {
        refuseSubtitleText(request.url())
        val httpRequest = buildOkHttpRequest(request)
        client.newCall(httpRequest).execute().use { httpResponse ->
            if (httpResponse.code == 429) {
                logger.warn(
                    "provider_http event=rate_limited method={} path={}",
                    request.httpMethod(),
                    httpRequest.url.encodedPath,
                )
                runCatching { onProviderRefusal?.invoke() }
                throw ReCaptchaException("reCaptcha required", request.url())
            }

            return OkHttpExtractorResponseMapper.toExtractorResponse(httpResponse)
        }
    }

    override fun executeAsync(request: ExtractorRequest, callback: AsyncCallback): CancellableCall {
        refuseSubtitleText(request.url())
        val httpRequest = buildOkHttpRequest(request)
        val call: Call = client.newCall(httpRequest)
        val cancellableCall = CancellableCall(call)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { httpResponse ->
                    callback.onSuccess(OkHttpExtractorResponseMapper.toExtractorResponse(httpResponse))
                    cancellableCall.setFinished()
                }
            }

            override fun onFailure(call: Call, e: java.io.IOException) {
                callback.onError(e)
                cancellableCall.setFinished()
            }
        })

        return cancellableCall
    }

    override fun postStreaming(
        url: String,
        headers: MutableMap<String, MutableList<String>>?,
        dataToSend: ByteArray?,
        localization: Localization?,
    ): StreamingResponse {
        val request = ExtractorRequest.newBuilder()
            .post(url, dataToSend)
            .headers(headers)
            .localization(localization)
            .build()
        val httpRequest = buildOkHttpRequest(request)
        val httpResponse = streamingClient.newCall(httpRequest).execute()
        if (httpResponse.code == 429) {
            logger.warn(
                "provider_http event=rate_limited method=POST path={}",
                httpRequest.url.encodedPath,
            )
            httpResponse.close()
            runCatching { onProviderRefusal?.invoke() }
            throw ReCaptchaException("reCaptcha required", url)
        }
        return StreamingResponse(
            httpResponse.code,
            httpResponse.headers.toMultimap(),
            OkHttpStreamingBodyStream(httpResponse),
        )
    }

    private fun buildOkHttpRequest(request: ExtractorRequest): Request {
        val method = request.httpMethod()
        val dataToSend = request.dataToSend()
        val normalizedUrl = normalizeExtractorUrl(request.url())
        val body = dataToSend?.toRequestBody()
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody() else null
        val builder = Request.Builder()
            .url(normalizedUrl)
            .method(method, body)

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        BilibiliCookieContext.headerFor(normalizedUrl)?.let { builder.header("Cookie", it) }
        YoutubeAuthUserContext.headerFor(normalizedUrl)?.let {
            builder.header(YOUTUBE_AUTH_USER_HEADER, it)
        }

        return builder.build()
    }
}

internal fun normalizeExtractorUrl(url: String): String =
    if (url.startsWith("/")) "https://www.youtube.com$url" else url
