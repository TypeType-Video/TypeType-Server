package dev.typetype.server.services

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult

fun transientAwareFailure(error: Throwable, fallback: String): ExtractionResult.Failure {
    val mapped = StreamExtractionErrorMapper.map<Nothing>(error, fallback = fallback)
    if (mapped is ExtractionResult.Failure && mapped.kind in TRANSIENT_KINDS) return mapped
    return ExtractionResult.Failure(error.message ?: fallback)
}

private val TRANSIENT_KINDS = setOf(
    ExtractionFailureKind.ProviderAccessBlocked,
    ExtractionFailureKind.RateLimited,
    ExtractionFailureKind.UpstreamTimeout,
)
