package dev.typetype.server.routes

internal fun Float.isSupportedSabrPlaybackRate(): Boolean =
    isFinite() && this in 0.25f..4.0f
