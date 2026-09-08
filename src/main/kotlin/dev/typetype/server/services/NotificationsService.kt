package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.NotificationReadItemsTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.models.MarkNotificationsReadResponse
import dev.typetype.server.models.NotificationItem
import dev.typetype.server.models.NotificationsResponse
import dev.typetype.server.models.UnreadCountResponse
import dev.typetype.server.models.VideoItem
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest

class NotificationsService(
    private val subscriptionFeedService: SubscriptionFeedService,
) {
    private val unreadCache = ConcurrentHashMap<String, CachedUnread>()

    suspend fun getNotifications(userId: String, page: Int, limit: Int): NotificationsResponse {
        val feed = loadFeed(userId)
        if (!feed.available) {
            return NotificationsResponse(emptyList(), cachedUnread(userId), null, false)
        }
        val items = withReadState(buildItems(feed.videos), userId)
        val unreadCount = unreadCount(items, userId)
        val from = page * limit
        if (from >= items.size) {
            return NotificationsResponse(emptyList(), unreadCount, null, feed.available)
        }
        val to = minOf(from + limit, items.size)
        val nextpage = if (to < items.size) (page + 1).toString() else null
        return NotificationsResponse(items.subList(from, to), unreadCount, nextpage, true)
    }

    suspend fun getUnreadCount(userId: String): UnreadCountResponse {
        val cached = unreadCache[userId]
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) return UnreadCountResponse(cached.value, true)
        val feed = loadFeed(userId)
        if (!feed.available) return UnreadCountResponse(cachedUnread(userId), false)
        val value = unreadCount(withReadState(buildItems(feed.videos), userId), userId)
        return UnreadCountResponse(value, true)
    }

    suspend fun markAllRead(userId: String): MarkNotificationsReadResponse {
        val feed = loadFeed(userId)
        if (!feed.available) {
            return MarkNotificationsReadResponse(System.currentTimeMillis(), cachedUnread(userId), false)
        }
        val items = buildItems(feed.videos)
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            items.forEach { item ->
                NotificationReadItemsTable.insertIgnore {
                    it[NotificationReadItemsTable.userId] = userId
                    it[notificationId] = item.id
                    it[readAt] = now
                }
            }
            val latestUploaded = items.maxOfOrNull { it.createdAt } ?: 0L
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

    suspend fun markRead(userId: String, notificationId: String): MarkNotificationsReadResponse {
        val feed = loadFeed(userId)
        if (!feed.available) {
            return MarkNotificationsReadResponse(System.currentTimeMillis(), cachedUnread(userId), false)
        }
        val items = buildItems(feed.videos)
        val now = System.currentTimeMillis()
        if (items.any { it.id == notificationId }) {
            DatabaseFactory.query {
                NotificationReadItemsTable.insertIgnore {
                    it[NotificationReadItemsTable.userId] = userId
                    it[NotificationReadItemsTable.notificationId] = notificationId
                    it[readAt] = now
                }
            }
        }
        val unread = unreadCount(withReadState(items, userId), userId)
        return MarkNotificationsReadResponse(now, unread, true)
    }

    private suspend fun loadFeed(userId: String): SubscriptionFeedAvailability =
        runCatching { subscriptionFeedService.getAllWithAvailability(userId) }
            .getOrElse { SubscriptionFeedAvailability(emptyList(), false) }

    private suspend fun withReadState(items: List<NotificationItem>, userId: String): List<NotificationItem> {
        val readIds = DatabaseFactory.query {
            NotificationReadItemsTable.selectAll().where { NotificationReadItemsTable.userId eq userId }
                .mapTo(HashSet()) { it[NotificationReadItemsTable.notificationId] }
        }
        val legacyWatermark = DatabaseFactory.query {
            NotificationStatesTable.selectAll().where { NotificationStatesTable.userId eq userId }
                .singleOrNull()?.get(NotificationStatesTable.subscriptionLastSeenUploaded) ?: 0L
        }
        return items.map { item ->
            item.copy(read = item.id in readIds || (readIds.isEmpty() && item.createdAt <= legacyWatermark))
        }
    }

    private fun buildItems(videos: List<VideoItem>): List<NotificationItem> = videos.asSequence()
        .map { video -> video to RssVideoMetadata.publishedAtMillis(video) }
        .filter { (_, createdAt) -> createdAt > 0L }
        .distinctBy { (video, _) -> notificationKey(video) }
        .sortedByDescending { it.second }
        .map { it.toNotificationItem() }
        .toList()

    private suspend fun unreadCount(items: List<NotificationItem>, userId: String): Int {
        val value = items.count { !it.read }
        unreadCache[userId] = CachedUnread(value, System.currentTimeMillis() + UNREAD_CACHE_TTL_MS)
        return value
    }

    private fun cachedUnread(userId: String): Int = unreadCache[userId]?.value ?: 0

    private fun notificationKey(video: VideoItem): String {
        val serviceId = RssVideoMetadata.serviceId(video)
        val channelIdentity = video.uploaderUrl.trim().ifBlank { video.uploaderAvatarUrl.trim() }
            .ifBlank { video.uploaderName.trim() }
        val videoIdentity = video.url.trim().ifBlank { video.id.trim() }
            .ifBlank { "${video.title.trim()}:${RssVideoMetadata.publishedAtMillis(video)}" }
        return "$serviceId|$channelIdentity|$videoIdentity"
    }

    private fun Pair<VideoItem, Long>.toNotificationItem(): NotificationItem {
        val video = first
        val createdAt = second
        val serviceId = RssVideoMetadata.serviceId(video)
        return NotificationItem(
            id = notificationId(video),
            type = "subscription_new_video",
            title = "${video.uploaderName} uploaded a new video",
            createdAt = createdAt,
            publishedAt = createdAt,
            channelUrl = video.uploaderUrl,
            channelName = video.uploaderName,
            channelAvatarUrl = video.uploaderAvatarUrl,
            serviceId = serviceId,
            serviceName = serviceName(serviceId),
            video = video,
        )
    }

    private fun notificationId(video: VideoItem): String =
        MessageDigest.getInstance("SHA-256").digest(notificationKey(video).toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

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
