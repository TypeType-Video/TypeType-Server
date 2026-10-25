package dev.typetype.server.services

data class SabrInfoFetchKey(
    val videoId: String,
    val startBucket: Long,
    val cachedFirst: Boolean,
)
