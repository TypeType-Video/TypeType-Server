package dev.typetype.server.services

sealed interface TokenServiceResult<out T> {
    data class Success<T>(val value: T) : TokenServiceResult<T>

    data class Failure(
        val reason: TokenServiceFailureReason,
        val status: Int? = null,
        val error: String? = null,
    ) : TokenServiceResult<Nothing> {
        fun isProviderRefusal(): Boolean =
            status == 403 || status == 429 || PROVIDER_REFUSAL_MARKERS.any { error?.contains(it, ignoreCase = true) == true }

        fun isTransient(): Boolean =
            reason == TokenServiceFailureReason.TIMEOUT ||
                reason == TokenServiceFailureReason.NETWORK ||
                status == 502 || status == 503 || status == 504
    }

    fun valueOrNull(): T? = (this as? Success<T>)?.value

    private companion object {
        val PROVIDER_REFUSAL_MARKERS = listOf("LOGIN_REQUIRED", "not a bot", "player response is not valid")
    }
}
