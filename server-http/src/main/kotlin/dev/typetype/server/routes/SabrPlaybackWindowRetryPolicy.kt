package dev.typetype.server.routes

internal fun playbackWindowRetryAfterMs(
    isLive: Boolean,
    initialWindowReady: Boolean,
    defaultRetryAfterMs: Long,
): Long = if (!isLive && !initialWindowReady) FIRST_PENDING_RETRY_MS else defaultRetryAfterMs

private const val FIRST_PENDING_RETRY_MS = 100L
