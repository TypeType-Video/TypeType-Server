package dev.typetype.server.portability

import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.charset.StandardCharsets
import java.util.UUID

object TypeTypePortabilityLibraryImport {
    fun write(
        userId: String,
        category: PortabilityCategory,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
        onRecord: () -> Unit,
    ): Long {
        clearIfReplacing(userId, category, policy)
        var count = 0L
        source.forEach(category) { record ->
            count += when (record) {
                is PortabilityWatchLater -> watchLater(userId, record)
                is PortabilityFavorite -> favorite(userId, record)
                is PortabilityProgress -> progress(userId, record)
                is PortabilitySearchHistory -> searchHistory(userId, record)
                is PortabilitySavedPlaylist -> savedPlaylist(userId, record)
                else -> 0
            }
            onRecord()
        }
        return count
    }

    private fun clearIfReplacing(
        userId: String,
        category: PortabilityCategory,
        policy: PortabilityDuplicatePolicy,
    ) {
        if (policy != PortabilityDuplicatePolicy.REPLACE) return
        when (category) {
            PortabilityCategory.WATCH_LATER -> WatchLaterTable.deleteWhere { WatchLaterTable.userId eq userId }
            PortabilityCategory.FAVORITES -> FavoritesTable.deleteWhere { FavoritesTable.userId eq userId }
            PortabilityCategory.PROGRESS -> ProgressTable.deleteWhere { ProgressTable.userId eq userId }
            PortabilityCategory.SEARCH_HISTORY -> SearchHistoryTable.deleteWhere { SearchHistoryTable.userId eq userId }
            PortabilityCategory.SAVED_PLAYLISTS -> SavedPlaylistsTable.deleteWhere { SavedPlaylistsTable.userId eq userId }
            else -> error("Unsupported library portability category")
        }
    }

    private fun watchLater(userId: String, record: PortabilityWatchLater): Int = WatchLaterTable.insertIgnore {
        it[WatchLaterTable.userId] = userId
        it[url] = record.video.url
        it[title] = record.video.title
        it[thumbnail] = record.video.thumbnailUrl
        it[duration] = record.video.durationSeconds
        it[addedAt] = record.addedAt
        it[channelName] = record.video.channelName
        it[channelUrl] = record.video.channelUrl
        it[channelAvatar] = record.video.channelAvatarUrl
        it[viewCount] = record.video.viewCount
        it[publishedAt] = record.video.publishedAt
    }.insertedCount

    private fun favorite(userId: String, record: PortabilityFavorite): Int = FavoritesTable.insertIgnore {
        it[FavoritesTable.userId] = userId
        it[videoUrl] = record.video.url
        it[favoritedAt] = record.favoritedAt
        it[title] = record.video.title
        it[thumbnail] = record.video.thumbnailUrl
        it[duration] = record.video.durationSeconds
        it[channelName] = record.video.channelName
        it[channelUrl] = record.video.channelUrl
        it[channelAvatar] = record.video.channelAvatarUrl
        it[viewCount] = record.video.viewCount
        it[publishedAt] = record.video.publishedAt
    }.insertedCount

    private fun progress(userId: String, record: PortabilityProgress): Int = ProgressTable.insertIgnore {
        it[ProgressTable.userId] = userId
        it[videoUrl] = record.videoUrl
        it[position] = record.positionSeconds.coerceAtLeast(0L)
        it[updatedAt] = record.updatedAt
    }.insertedCount

    private fun searchHistory(userId: String, record: PortabilitySearchHistory): Int {
        val exists = SearchHistoryTable.selectAll().where {
            (SearchHistoryTable.userId eq userId) and
                (SearchHistoryTable.term eq record.term) and
                (SearchHistoryTable.searchedAt eq record.searchedAt)
        }.empty().not()
        if (exists) return 0
        return SearchHistoryTable.insertIgnore {
            it[id] = UUID.randomUUID().toString()
            it[SearchHistoryTable.userId] = userId
            it[term] = record.term
            it[searchedAt] = record.searchedAt
        }.insertedCount
    }

    private fun savedPlaylist(userId: String, record: PortabilitySavedPlaylist): Int =
        SavedPlaylistsTable.insertIgnore {
            it[id] = stableLibraryId(userId, record.url)
            it[SavedPlaylistsTable.userId] = userId
            it[publicPlaylistId] = record.sourceId
            it[url] = record.url
            it[title] = record.title
            it[thumbnailUrl] = record.thumbnailUrl
            it[uploaderName] = record.uploaderName
            it[streamCount] = record.streamCount
            it[playlistType] = record.playlistType
            it[savedAt] = record.savedAt
        }.insertedCount
}

private fun stableLibraryId(userId: String, value: String): String = UUID.nameUUIDFromBytes(
    "$userId:saved:$value".toByteArray(StandardCharsets.UTF_8),
).toString()
