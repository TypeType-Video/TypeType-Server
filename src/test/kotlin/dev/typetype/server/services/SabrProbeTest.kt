package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment

internal data class SabrProbeFetchResult(
    val segment: SabrMediaSegment?,
    val elapsedMs: Long,
    val timedOut: Boolean,
    val error: Throwable?,
)
