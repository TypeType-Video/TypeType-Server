package dev.typetype.server.services

data class SabrPlaybackPreparation(
    val holder: SabrSessionHolder,
    val startTimeMs: Long,
    val ready: Boolean,
)
