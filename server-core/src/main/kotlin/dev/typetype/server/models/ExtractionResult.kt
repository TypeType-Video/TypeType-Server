package dev.typetype.server.models

sealed class ExtractionResult<out T> {
    data class Success<T>(val data: T) : ExtractionResult<T>()
    data class Failure(
        val message: String,
        val code: String = "error",
        val kind: ExtractionFailureKind = ExtractionFailureKind.Unknown,
        val retryAfterSeconds: Long? = null,
    ) : ExtractionResult<Nothing>()
    data class BadRequest(val message: String, val code: String = "error") : ExtractionResult<Nothing>()
}

enum class ExtractionFailureKind {
    Unknown,
    YoutubeSessionRejected,
    LiveEventNotStarted,
    ScheduledPremiere,
    ContentUnavailable,
    ProviderAccessBlocked,
    RateLimited,
    UpstreamTimeout,
    Unavailable,
}
