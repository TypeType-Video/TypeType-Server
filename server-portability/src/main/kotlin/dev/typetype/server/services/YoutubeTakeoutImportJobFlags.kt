package dev.typetype.server.services

data class YoutubeTakeoutImportJobFlags(
    val parseCompleted: Boolean,
    val importStarted: Boolean,
    val importCompleted: Boolean,
)
