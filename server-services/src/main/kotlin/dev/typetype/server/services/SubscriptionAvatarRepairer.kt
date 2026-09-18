package dev.typetype.server.services

import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.models.SubscriptionItem
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object SubscriptionAvatarRepairer {
    fun repair(userId: String, items: List<SubscriptionItem>): List<SubscriptionItem> {
        val candidateUrls = items.filter { it.avatarUrl.isBlank() }
            .map { it.channelUrl }
            .distinct()
        if (candidateUrls.isEmpty()) return items
        val avatars = knownAvatars(userId = userId, channelUrls = candidateUrls)
            .entries
            .take(MAX_AVATAR_REPAIR_PER_REQUEST)
            .associate { it.toPair() }
        if (avatars.isEmpty()) return items
        avatars.forEach { (channelUrl, avatarUrl) -> updateAvatar(userId, channelUrl, avatarUrl) }
        return items.map { item -> avatars[item.channelUrl]?.let { item.copy(avatarUrl = it) } ?: item }
    }

    private fun knownAvatars(userId: String, channelUrls: List<String>): Map<String, String> {
        val avatars = linkedMapOf<String, String>()
        historyAvatars(userId, channelUrls).forEach { avatars.putIfAbsent(it.key, it.value) }
        playlistAvatars(userId, channelUrls).forEach { avatars.putIfAbsent(it.key, it.value) }
        watchLaterAvatars(userId, channelUrls).forEach { avatars.putIfAbsent(it.key, it.value) }
        favoriteAvatars(userId, channelUrls).forEach { avatars.putIfAbsent(it.key, it.value) }
        return avatars
    }

    private fun historyAvatars(userId: String, channelUrls: List<String>): Map<String, String> = HistoryTable.selectAll()
        .where { avatarSourceFilter(userId, channelUrls, HistoryTable.userId, HistoryTable.channelUrl, HistoryTable.channelAvatar) }
        .orderBy(HistoryTable.watchedAt to SortOrder.DESC)
        .limit(MAX_AVATAR_SOURCE_ROWS)
        .associateAvatarRows(HistoryTable.channelUrl, HistoryTable.channelAvatar)

    private fun playlistAvatars(userId: String, channelUrls: List<String>): Map<String, String> = PlaylistVideosTable.selectAll()
        .where { avatarSourceFilter(userId, channelUrls, PlaylistVideosTable.userId, PlaylistVideosTable.channelUrl, PlaylistVideosTable.channelAvatar) }
        .limit(MAX_AVATAR_SOURCE_ROWS)
        .associateAvatarRows(PlaylistVideosTable.channelUrl, PlaylistVideosTable.channelAvatar)

    private fun watchLaterAvatars(userId: String, channelUrls: List<String>): Map<String, String> = WatchLaterTable.selectAll()
        .where { avatarSourceFilter(userId, channelUrls, WatchLaterTable.userId, WatchLaterTable.channelUrl, WatchLaterTable.channelAvatar) }
        .orderBy(WatchLaterTable.addedAt to SortOrder.DESC)
        .limit(MAX_AVATAR_SOURCE_ROWS)
        .associateAvatarRows(WatchLaterTable.channelUrl, WatchLaterTable.channelAvatar)

    private fun favoriteAvatars(userId: String, channelUrls: List<String>): Map<String, String> = FavoritesTable.selectAll()
        .where { avatarSourceFilter(userId, channelUrls, FavoritesTable.userId, FavoritesTable.channelUrl, FavoritesTable.channelAvatar) }
        .orderBy(FavoritesTable.favoritedAt to SortOrder.DESC)
        .limit(MAX_AVATAR_SOURCE_ROWS)
        .associateAvatarRows(FavoritesTable.channelUrl, FavoritesTable.channelAvatar)

    private fun updateAvatar(userId: String, channelUrl: String, avatarUrl: String): Int = SubscriptionsTable.update({
        (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.channelUrl eq channelUrl) and (SubscriptionsTable.avatarUrl eq "")
    }) {
        it[SubscriptionsTable.avatarUrl] = avatarUrl
    }

    private fun avatarSourceFilter(
        userId: String,
        channelUrls: List<String>,
        userColumn: Column<String>,
        urlColumn: Column<String>,
        avatarColumn: Column<String>,
    ) = (userColumn eq userId) and (urlColumn inList channelUrls) and (avatarColumn neq "")

    private fun Iterable<ResultRow>.associateAvatarRows(
        urlColumn: Column<String>,
        avatarColumn: Column<String>,
    ): Map<String, String> = mapNotNull { row ->
        val avatar = row[avatarColumn].trim()
        if (avatar.isProxyableAvatar()) ChannelUrlCanonicalizer.canonicalize(row[urlColumn]) to avatar else null
    }.distinctBy { it.first }.toMap()

    private fun String.isProxyableAvatar(): Boolean = startsWith("https://") || startsWith("http://")

    private const val MAX_AVATAR_REPAIR_PER_REQUEST = 25
    private const val MAX_AVATAR_SOURCE_ROWS = 100
}
