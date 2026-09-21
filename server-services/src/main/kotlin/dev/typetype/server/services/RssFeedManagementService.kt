package dev.typetype.server.services

import dev.typetype.server.models.AdminRssFeedsPage
import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.models.RssFeedRequest
import dev.typetype.server.models.RssFeedSecretItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class RssFeedManagementService public constructor(
    private val settings: AdminSettingsService,
    private val subscriptions: SubscriptionsService,
    private val repository: RssFeedRepository = RssFeedRepository(),
    private val adminRepository: RssFeedAdminRepository = RssFeedAdminRepository(),
    private val secrets: RssFeedSecret = RssFeedSecret(),
) {
    suspend fun list(userId: String): List<RssFeedItem> {
        requireAvailable(userId)
        return repository.list(userId)
    }

    suspend fun create(userId: String, request: RssFeedRequest): RssFeedSecretItem {
        val config = requireAvailable(userId)
        val normalized = normalize(userId, request)
        val secret = secrets.create()
        val feed = repository.createWithinLimit(
            userId,
            UUID.randomUUID().toString(),
            secrets.hash(secret),
            normalized,
            config.rssMaxFeedsPerUser,
        ) ?: throw RssFeedException("RSS feed limit reached", "rss_feed_limit_reached")
        return RssFeedSecretItem(feed, feedUrl(config.rssPublicBaseUrl!!, feed.id, secret))
    }

    suspend fun update(userId: String, feedId: String, request: RssFeedRequest): RssFeedItem {
        requireAvailable(userId)
        val normalized = normalize(userId, request)
        return repository.update(userId, feedId, normalized)
            ?: throw RssFeedException("RSS feed not found", "rss_feed_not_found")
    }

    suspend fun setEnabled(userId: String, feedId: String, enabled: Boolean): RssFeedItem {
        requireAvailable(userId)
        return repository.setEnabled(userId, feedId, enabled)
            ?: throw RssFeedException("RSS feed not found", "rss_feed_not_found")
    }

    suspend fun regenerate(userId: String, feedId: String): RssFeedSecretItem {
        val config = requireAvailable(userId)
        val secret = secrets.create()
        val feed = repository.replaceToken(userId, feedId, secrets.hash(secret))
            ?: throw RssFeedException("RSS feed not found", "rss_feed_not_found")
        return RssFeedSecretItem(feed, feedUrl(config.rssPublicBaseUrl!!, feed.id, secret))
    }

    suspend fun delete(userId: String, feedId: String) {
        requireAvailable(userId)
        if (!repository.delete(userId, feedId)) throw RssFeedException("RSS feed not found", "rss_feed_not_found")
    }

    suspend fun adminList(page: Int, limit: Int): AdminRssFeedsPage {
        val (items, total) = adminRepository.list(page, limit)
        return AdminRssFeedsPage(items, page, limit, total)
    }

    suspend fun adminSetEnabled(feedId: String, enabled: Boolean): RssFeedItem =
        repository.setEnabledByAdmin(feedId, enabled)
            ?: throw RssFeedException("RSS feed not found", "rss_feed_not_found")

    suspend fun adminDelete(feedId: String) {
        if (!repository.deleteByAdmin(feedId)) throw RssFeedException("RSS feed not found", "rss_feed_not_found")
    }

    suspend fun adminSetUserEnabled(userId: String, enabled: Boolean) {
        if (!adminRepository.userExists(userId)) {
            throw RssFeedException("User not found", "rss_user_not_found")
        }
        repository.setUserEnabled(userId, enabled)
    }

    private suspend fun requireAvailable(userId: String) = settings.get().also { config ->
        if (!config.rssEnabled || config.rssPublicBaseUrl == null) {
            throw RssFeedException("RSS feeds are disabled", "rss_disabled")
        }
        if (!repository.userEnabled(userId)) throw RssFeedException("RSS feeds are disabled for this account", "rss_user_disabled")
    }

    private suspend fun normalize(userId: String, request: RssFeedRequest): RssFeedRequest {
        val name = request.name.trim()
        if (name.length !in 1..100) throw RssFeedException("Name must contain 1 to 100 characters", "rss_invalid_name")
        if (request.scope !in SCOPES) throw RssFeedException("Invalid RSS scope", "rss_invalid_scope")
        val services = request.serviceIds.distinct().sorted()
        if (services.isEmpty() || services.any { it !in SERVICES }) {
            throw RssFeedException("Select at least one supported service", "rss_invalid_services")
        }
        if (!request.hasSelectedType()) throw RssFeedException("Select at least one content type", "rss_invalid_types")
        val channels = when (request.scope) {
            "all" -> emptyList()
            "channels" -> validateChannels(userId, request.channelUrls)
            else -> throw RssFeedException("Invalid RSS scope", "rss_invalid_scope")
        }
        return request.copy(
            name = name,
            channelUrls = channels,
            serviceIds = services,
        )
    }

    private suspend fun validateChannels(userId: String, rawChannels: List<String>): List<String> {
        val channels = rawChannels.map(ChannelUrlCanonicalizer::canonicalize).filter(String::isNotBlank).distinct()
        if (channels.isEmpty() || channels.size > 100) {
            throw RssFeedException("Select between 1 and 100 subscribed channels", "rss_invalid_channels")
        }
        val subscribed = subscriptions.getAll(userId).map { it.channelUrl }.toSet()
        if (channels.any { it !in subscribed }) {
            throw RssFeedException("RSS channels must belong to your subscriptions", "rss_channel_not_subscribed")
        }
        return channels.sorted()
    }

    private fun feedUrl(baseUrl: String, feedId: String, secret: String): String =
        "$baseUrl/api/rss/feeds/$feedId.xml?token=${URLEncoder.encode(secret, StandardCharsets.UTF_8)}"

    private fun RssFeedRequest.hasSelectedType(): Boolean =
        includeVideos || includeShorts || includeLive || includeUpcoming

    companion object {
        private val SCOPES = setOf("all", "channels")
        private val SERVICES = setOf(0, 5, 6)
    }
}

class RssFeedException(message: String, val code: String) : IllegalArgumentException(message)
