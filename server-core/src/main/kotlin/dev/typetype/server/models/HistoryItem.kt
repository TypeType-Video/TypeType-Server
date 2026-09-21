package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(
    val id: String = "",
    val url: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val channelUrl: String,
    val channelAvatar: String = "",
    val duration: Long,
    val progress: Long,
    val watchedAt: Long = 0L,
)
