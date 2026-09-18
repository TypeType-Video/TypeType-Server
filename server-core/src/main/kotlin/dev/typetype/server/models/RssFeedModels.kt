package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RssFeedRequest(
    val name: String,
    val scope: String = "all",
    val channelUrls: List<String> = emptyList(),
    val serviceIds: List<Int> = listOf(0, 5, 6),
    val includeVideos: Boolean = true,
    val includeShorts: Boolean = true,
    val includeLive: Boolean = true,
    val includeUpcoming: Boolean = true,
)

@Serializable
data class RssFeedItem(
    val id: String,
    val name: String,
    val scope: String,
    val channelUrls: List<String>,
    val serviceIds: List<Int>,
    val includeVideos: Boolean,
    val includeShorts: Boolean,
    val includeLive: Boolean,
    val includeUpcoming: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
)

@Serializable
data class RssFeedSecretItem(val feed: RssFeedItem, val feedUrl: String)

@Serializable
data class RssFeedEnabledRequest(val enabled: Boolean)

@Serializable
data class RssUserPolicyRequest(val enabled: Boolean)

@Serializable
data class AdminRssFeedItem(
    val feed: RssFeedItem,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val userRssEnabled: Boolean,
    val userSuspended: Boolean,
)

@Serializable
data class AdminRssFeedsPage(
    val items: List<AdminRssFeedItem>,
    val page: Int,
    val limit: Int,
    val total: Long,
)
