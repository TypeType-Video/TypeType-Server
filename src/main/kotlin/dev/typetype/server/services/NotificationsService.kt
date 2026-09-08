package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.models.MarkNotificationsReadResponse
import dev.typetype.server.models.NotificationItem
import dev.typetype.server.models.NotificationsResponse
import dev.typetype.server.models.UnreadCountResponse
import dev.typetype.server.models.VideoItem
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class NotificationsService(
    private val subscriptionFeedService: SubscriptionFeedService,
) {
    private val unreadCache = ConcurrentHashMap<String, CachedUnread>()

    suspend fun getNotifications(userId: String, page: Int, limit: Int): NotificationsResponse {
        val feed = loadFeed(userId)
        val items = buildItems(feed.videos)
        val unreadCount = if (feed.available) unreadCount(items, userId) else cachedUnread(userId)
        val from = page * limit
        if (from >= items.size) {
            return NotificationsResponse(emptyList(), unreadCount, null, feed.available)
        }
        val to = minOf(from + limit, items.size)
        val nextpage = if (to < items.size) (page + 1).toString() else null
        return NotificationsResponse(items.subList(from, to), unreadCount, nextpage, feed.available)
    }

    suspend fun getUnreadCount(userId: String): UnreadCountResponse {
        val cached = unreadCache[userId]
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) return UnreadCountResponse(cached.value, true)
        val feed = loadFeed(userId)
        if (!feed.available) return UnreadCountResponse(cachedUnread(userId), false)
        val value = unreadCount(buildItems(feed.videos), userId)
        return UnreadCountResponse(value, true)
    }

    suspend fun markAllRead(userId: String): MarkNotificationsReadResponse {
        val feed = loadFeed(userId)
        if (!feed.available) {
            return MarkNotificationsReadResponse(System.currentTimeMillis(), cachedUnread(userId), false)
        }
        val latestUploaded = buildItems(feed.videos).firstOrNull()?.createdAt ?: 0L
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            val updated = NotificationStatesTable.update({ NotificationStatesTable.userId eq userId }) {
                it[subscriptionLastSeenUploaded] = latestUploaded
                it[updatedAt] = now
            }
            if (updated == 0) {
                NotificationStatesTable.insert {
                    it[NotificationStatesTable.userId] = userId
                    it[NotificationStatesTable.subscriptionLastSeenUploaded] = latestUploaded
                    it[NotificationStatesTable.updatedAt] = now
                }
            }
        }
        unreadCache[userId] = CachedUnread(0, now + UNREAD_CACHE_TTL_MS)
        return MarkNotificationsReadResponse(now, 0, true)
    }

    private suspend fun loadFeed(userId: String): SubscriptionFeedAvailability =
        runCatching { subscriptionFeedService.getAllWithAvailability(userId) }
            .getOrElse { SubscriptionFeedAvailability(emptyList(), false) }

    private fun buildItems(videos: List<VideoItem>): List<NotificationItem> = videos.asSequence()
        .filter { it.uploaded > 0L }
        .distinctBy(::notificationKey)
        .sortedByDescending { it.uploaded }
        .map { it.toNotificationItem() }
        .toList()

    private suspend fun unreadCount(items: List<NotificationItem>, userId: String): Int {
        val lastSeenUploaded = DatabaseFactory.query {
            NotificationStatesTable.selectAll().where { NotificationStatesTable.userId eq userId }
                .singleOrNull()?.get(NotificationStatesTable.subscriptionLastSeenUploaded) ?: 0L
        }
        val value = items.count { it.createdAt > lastSeenUploaded }
        unreadCache[userId] = CachedUnread(value, System.currentTimeMillis() + UNREAD_CACHE_TTL_MS)
        return value
    }

    private fun cachedUnread(userId: String): Int = unreadCache[userId]?.value ?: 0

    private fun notificationKey(video: VideoItem): String {
        val serviceId = RssVideoMetadata.serviceId(video)
        val videoIdentity = video.url.ifBlank { video.id }
        return "$serviceId:${video.uploaderUrl}:$videoIdentity"
    }

    private fun VideoItem.toNotificationItem(): NotificationItem {
        val serviceId = RssVideoMetadata.serviceId(this)
        return NotificationItem(
            type = "subscription_new_video",
            title = "$uploaderName uploaded a new video",
            createdAt = uploaded,
            publishedAt = uploaded,
            channelUrl = uploaderUrl,
            channelName = uploaderName,
            channelAvatarUrl = uploaderAvatarUrl,
            serviceId = serviceId,
            serviceName = serviceName(serviceId),
            video = this,
        )
    }

    private data class CachedUnread(val value: Int, val expiresAt: Long)

    private companion object {
        const val UNREAD_CACHE_TTL_MS = 30_000L

        fun serviceName(serviceId: Int): String = when (serviceId) {
            YOUTUBE_SERVICE_ID -> "YouTube"
            BILIBILI_SERVICE_ID -> "BiliBili"
            NICONICO_SERVICE_ID -> "NicoNico"
            SOUNDCLOUD_SERVICE_ID -> "SoundCloud"
            MEDIA_CCC_SERVICE_ID -> "MediaCCC"
            else -> "Video service"
        }
    }
}
