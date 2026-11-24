package dev.typetype.server.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val config: GeminiConfig,
    timeoutMs: Long,
    proxy: Proxy? = null,
    baseClient: OkHttpClient = OkHttpClient(),
) : AiProvider {
    override val id: String = PROVIDER_ID
    override val model: String = config.model

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            proxy?.let {
                proxy(it)
                connectionPool(ConnectionPool(PROXY_POOL_MAX_IDLE, PROXY_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
            }
        }
        .build()

    private val endpoint: String = buildEndpoint(config.baseUrl, config.model)

    override suspend fun complete(request: AiCompletionRequest): AiCompletionResult {
        val apiKey = config.apiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: return AiCompletionResult.Disabled
        val payload = GeminiCodec.buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header(API_KEY_HEADER, apiKey)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            for (attempt in 0 until MAX_ATTEMPTS) {
                try {
                    return@withContext client.newCall(httpRequest).execute().use { response ->
                        val durationMs = (System.nanoTime() - startedAt) / MS_PER_NANOSECOND
                        mapResponse(response.code, response.body.string(), apiKey, durationMs)
                    }
                } catch (e: IOException) {
                    client.connectionPool.evictAll()
                    val durationMs = (System.nanoTime() - startedAt) / MS_PER_NANOSECOND
                    val redacted = GeminiCodec.redactApiKey(e.message.orEmpty(), apiKey)
                    val willRetry = attempt == 0 && e !is SocketTimeoutException
                    logger.warn(
                        "gemini_request event=io_error model={} attempt={} retry={} durationMs={} errorType={} message={}",
                        model,
                        attempt + 1,
                        willRetry,
                        durationMs,
                        e.javaClass.simpleName,
                        redacted,
                    )
                    if (!willRetry) {
                        return@withContext AiCompletionResult.Failed("upstream_unavailable", retryable = true)
                    }
                }
            }
            AiCompletionResult.Failed("upstream_unavailable", retryable = true)
        }
    }

    private fun mapResponse(
        statusCode: Int,
        responseBody: String,
        apiKey: String,
        durationMs: Long,
    ): AiCompletionResult = when (statusCode) {
        HTTP_OK -> {
            val text = GeminiCodec.parseSuccessText(responseBody)
            if (text != null) {
                AiCompletionResult.Success(
                    text = text,
                    model = model,
                    providerId = id,
                    cached = false,
                )
            } else {
                logger.warn("gemini_request event=empty_response model={} status=200 durationMs={}", model, durationMs)
                AiCompletionResult.Failed("empty_response", retryable = false)
            }
        }
        HTTP_TOO_MANY_REQUESTS -> {
            logger.warn("gemini_request event=rate_limited model={} status=429 durationMs={}", model, durationMs)
            AiCompletionResult.RateLimited("upstream_rate_limited")
        }
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
            logger.warn(
                "gemini_request event=auth_error model={} status={} durationMs={}",
                model,
                statusCode,
                durationMs,
            )
            AiCompletionResult.Failed("upstream_auth_error", retryable = false)
        }
        HTTP_BAD_REQUEST -> {
            val reason = GeminiCodec.extractErrorReason(statusCode, responseBody, apiKey)
            logger.warn(
                "gemini_request event=bad_request model={} status={} durationMs={} reason={}",
                model,
                statusCode,
                durationMs,
                reason,
            )
            AiCompletionResult.Failed(reason, retryable = false)
        }
        in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END -> {
            val reason = GeminiCodec.extractErrorReason(statusCode, responseBody, apiKey)
            logger.warn(
                "gemini_request event=server_error model={} status={} durationMs={} reason={}",
                model,
                statusCode,
                durationMs,
                reason,
            )
            AiCompletionResult.Failed(reason, retryable = true)
        }
        else -> {
            val reason = GeminiCodec.extractErrorReason(statusCode, responseBody, apiKey)
            logger.warn(
                "gemini_request event=unexpected_status model={} status={} durationMs={} reason={}",
                model,
                statusCode,
                durationMs,
                reason,
            )
            AiCompletionResult.Failed(reason, retryable = false)
        }
    }

    private fun buildEndpoint(baseUrl: String, modelName: String): String {
        val encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8).replace("+", "%20")
        return "$baseUrl/v1beta/models/$encodedModel:generateContent"
    }

    companion object {
        const val PROVIDER_ID: String = "gemini"
        private const val API_KEY_HEADER = "x-goog-api-key"
        private const val HTTP_OK = 200
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR_START = 500
        private const val HTTP_SERVER_ERROR_END = 599
        private const val MAX_ATTEMPTS = 2
        private const val PROXY_POOL_MAX_IDLE = 5
        private const val PROXY_KEEP_ALIVE_SECONDS = 25L
        private const val MS_PER_NANOSECOND = 1_000_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val logger = LoggerFactory.getLogger(GeminiProvider::class.java)
    }
}
