package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

data class TwitchLiveResponse(val status: Int, val body: String)

class TwitchLiveClient(
    private val config: TwitchConfig,
    client: OkHttpClient = OkHttpClient(),
) {
    private val calls = client.newBuilder().callTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS).build()
    private val streams = client.newBuilder().readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS).build()

    suspend fun get(path: String, query: Map<String, String> = emptyMap(), userToken: String? = null): TwitchLiveResponse =
        execute(request(url(path, query), userToken).get().build())

    suspend fun post(path: String, json: String, userToken: String? = null): TwitchLiveResponse =
        execute(request(url(path, emptyMap()), userToken).post(json.toRequestBody(JSON)).build())

    suspend fun put(path: String, userToken: String): TwitchLiveResponse =
        execute(request(url(path, emptyMap()), userToken).put(EMPTY_BODY.toRequestBody(JSON)).build())

    suspend fun delete(path: String, userToken: String): TwitchLiveResponse =
        execute(request(url(path, emptyMap()), userToken).delete().build())

    fun openStream(path: String): Response =
        streams.newCall(request(url(path, emptyMap()), null).get().build()).execute()

    private fun url(path: String, query: Map<String, String>): HttpUrl {
        val builder = (requireNotNull(config.serviceUrl) + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun request(url: HttpUrl, userToken: String?): Request.Builder {
        val builder = Request.Builder().url(url).header(SECRET_HEADER, requireNotNull(config.serviceSecret))
        userToken?.let { builder.header(TOKEN_HEADER, it) }
        return builder
    }

    private suspend fun execute(request: Request): TwitchLiveResponse = withContext(Dispatchers.IO) {
        runCatching {
            calls.newCall(request).execute().use { TwitchLiveResponse(it.code, it.body.string()) }
        }.getOrElse { TwitchLiveResponse(HTTP_BAD_GATEWAY, """{"error":"live_service_unavailable"}""") }
    }

    private companion object {
        const val SECRET_HEADER = "X-Live-Secret"
        const val TOKEN_HEADER = "X-Twitch-Token"
        const val HTTP_BAD_GATEWAY = 502
        const val STREAM_READ_TIMEOUT_S = 60L
        val JSON = "application/json".toMediaType()
        const val EMPTY_BODY = "{}"
    }
}
