package dev.typetype.server.downloader

import okhttp3.Callback
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.downloader.StreamingResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
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
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            proxySelector?.let(clientBuilder::proxySelector)
            val client = clientBuilder.build()
            val streamingClient = client.newBuilder()
                .readTimeout(streamingReadTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
            return OkHttpDownloader(client, streamingClient)
        }

        private const val STREAMING_READ_TIMEOUT_MS = 30_000L
        private const val YOUTUBE_AUTH_USER_HEADER = "X-Goog-AuthUser"
    }

    override fun execute(request: ExtractorRequest): Response {
        val httpRequest = buildOkHttpRequest(request)
        client.newCall(httpRequest).execute().use { httpResponse ->
            if (httpResponse.code == 429) {
                throw ReCaptchaException("reCaptcha required", request.url())
            }

            return OkHttpExtractorResponseMapper.toExtractorResponse(httpResponse)
        }
    }

    override fun executeAsync(request: ExtractorRequest, callback: AsyncCallback): CancellableCall {
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
            httpResponse.close()
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
        YoutubeAuthUserContext.headerFor(normalizedUrl)?.let {
            builder.header(YOUTUBE_AUTH_USER_HEADER, it)
        }

        return builder.build()
    }
}

internal fun normalizeExtractorUrl(url: String): String =
    if (url.startsWith("/")) "https://www.youtube.com$url" else url
