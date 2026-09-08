package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.UnknownHostException

internal class UnifiedPushSender(
    private val endpointValidator: UnifiedPushEndpointValidator = UnifiedPushEndpointValidator(),
    client: OkHttpClient = OkHttpClient(),
) : PushEndpointSender {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun send(endpoint: String, eventId: String, payload: String): PushSendResult = withContext(Dispatchers.IO) {
        val validated = endpointValidator.validate(endpoint)
        if (validated !is EndpointValidationResult.Valid) return@withContext PushSendResult.InvalidEndpoint
        val request = Request.Builder()
            .url(validated.uri.toString())
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("X-TypeType-Event-Id", eventId)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val requestClient = client.newBuilder()
            .dns(StaticPushDns(validated.uri.host.orEmpty(), validated.addresses))
            .build()
        runCatching { requestClient.newCall(request).execute().use { response -> response.code } }
            .fold(
                onSuccess = { code ->
                    when {
                        code in 200..299 -> PushSendResult.Delivered
                        code == 404 || code == 410 -> PushSendResult.InvalidEndpoint
                        else -> PushSendResult.Retry(code)
                    }
                },
                onFailure = { PushSendResult.Retry(null) },
            )
    }

    private class StaticPushDns(
        private val validatedHost: String,
        private val addresses: List<InetAddress>,
    ) : okhttp3.Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            if (hostname.equals(validatedHost, ignoreCase = true)) addresses
            else throw UnknownHostException("Unexpected push endpoint host")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal interface PushEndpointSender {
    suspend fun send(endpoint: String, eventId: String, payload: String): PushSendResult
}

internal sealed interface PushSendResult {
    data object Delivered : PushSendResult
    data object InvalidEndpoint : PushSendResult
    data class Retry(val statusCode: Int?) : PushSendResult
}
