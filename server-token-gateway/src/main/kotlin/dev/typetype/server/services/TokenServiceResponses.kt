package dev.typetype.server.services

import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException

internal object TokenServiceResponses {
    fun <T> read(response: Response, parse: (JSONObject) -> T?): TokenServiceResult<T> {
        val text = runCatching { response.body.string() }.getOrDefault("")
        if (!response.isSuccessful) return httpFailure(response.code, text)
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: return TokenServiceResult.Failure(TokenServiceFailureReason.INVALID_RESPONSE, response.code)
        return parse(json)?.let { TokenServiceResult.Success(it) }
            ?: TokenServiceResult.Failure(TokenServiceFailureReason.INVALID_RESPONSE, response.code)
    }

    fun failure(error: IOException): TokenServiceResult.Failure = TokenServiceResult.Failure(
        reason = if (error is InterruptedIOException) TokenServiceFailureReason.TIMEOUT else TokenServiceFailureReason.NETWORK,
        error = error.message,
    )

    private fun httpFailure(status: Int, text: String): TokenServiceResult.Failure = TokenServiceResult.Failure(
        reason = TokenServiceFailureReason.HTTP_STATUS,
        status = status,
        error = runCatching { JSONObject(text).optString("error") }.getOrNull()?.takeIf(String::isNotBlank),
    )
}
