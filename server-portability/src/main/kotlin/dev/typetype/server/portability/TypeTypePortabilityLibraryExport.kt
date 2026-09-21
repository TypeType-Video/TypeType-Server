package dev.typetype.server.portability

import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

object TypeTypePortabilityLibraryExport {
    fun write(userId: String, category: PortabilityCategory, sink: PortabilityRecordSink) {
        when (category) {
            PortabilityCategory.WATCH_LATER -> watchLater(userId, sink)
            PortabilityCategory.FAVORITES -> favorites(userId, sink)
            PortabilityCategory.PROGRESS -> progress(userId, sink)
            PortabilityCategory.SEARCH_HISTORY -> searchHistory(userId, sink)
            PortabilityCategory.SAVED_PLAYLISTS -> savedPlaylists(userId, sink)
            else -> error("Unsupported library portability category")
        }
    }

    private fun watchLater(userId: String, sink: PortabilityRecordSink) {
        WatchLaterTable.selectAll().where { WatchLaterTable.userId eq userId }
            .orderBy(WatchLaterTable.addedAt, SortOrder.ASC).forEach { row ->
                sink.write(
                    PortabilityWatchLater(
                        PortabilityVideo(
                            row[WatchLaterTable.url],
                            row[WatchLaterTable.title],
                            row[WatchLaterTable.thumbnail],
                            row[WatchLaterTable.duration],
                            row[WatchLaterTable.channelName],
                            row[WatchLaterTable.channelUrl],
                            row[WatchLaterTable.channelAvatar],
                            row[WatchLaterTable.viewCount],
                            row[WatchLaterTable.publishedAt],
                        ),
                        row[WatchLaterTable.addedAt],
                    ),
                )
            }
    }

    private fun favorites(userId: String, sink: PortabilityRecordSink) {
        FavoritesTable.selectAll().where { FavoritesTable.userId eq userId }
            .orderBy(FavoritesTable.favoritedAt, SortOrder.ASC).forEach { row ->
                sink.write(
                    PortabilityFavorite(
                        PortabilityVideo(
                            row[FavoritesTable.videoUrl],
                            row[FavoritesTable.title],
                            row[FavoritesTable.thumbnail],
                            row[FavoritesTable.duration],
                            row[FavoritesTable.channelName],
                            row[FavoritesTable.channelUrl],
                            row[FavoritesTable.channelAvatar],
                            row[FavoritesTable.viewCount],
                            row[FavoritesTable.publishedAt],
                        ),
                        row[FavoritesTable.favoritedAt],
                    ),
                )
            }
    }

    private fun progress(userId: String, sink: PortabilityRecordSink) {
        ProgressTable.selectAll().where { ProgressTable.userId eq userId }
            .orderBy(ProgressTable.updatedAt, SortOrder.ASC).forEach { row ->
                sink.write(
                    PortabilityProgress(
                        row[ProgressTable.videoUrl],
                        row[ProgressTable.position],
                        row[ProgressTable.updatedAt],
                    ),
                )
            }
    }

    private fun searchHistory(userId: String, sink: PortabilityRecordSink) {
        SearchHistoryTable.selectAll().where { SearchHistoryTable.userId eq userId }
            .orderBy(SearchHistoryTable.searchedAt, SortOrder.ASC).forEach { row ->
                sink.write(
                    PortabilitySearchHistory(
                        row[SearchHistoryTable.term],
                        row[SearchHistoryTable.searchedAt],
                    ),
                )
            }
    }

    private fun savedPlaylists(userId: String, sink: PortabilityRecordSink) {
        SavedPlaylistsTable.selectAll().where { SavedPlaylistsTable.userId eq userId }
            .orderBy(SavedPlaylistsTable.savedAt, SortOrder.ASC).forEach { row ->
                sink.write(
                    PortabilitySavedPlaylist(
                        row[SavedPlaylistsTable.publicPlaylistId],
                        row[SavedPlaylistsTable.url],
                        row[SavedPlaylistsTable.title],
                        row[SavedPlaylistsTable.thumbnailUrl],
                        row[SavedPlaylistsTable.uploaderName],
                        row[SavedPlaylistsTable.streamCount],
                        row[SavedPlaylistsTable.playlistType],
                        row[SavedPlaylistsTable.savedAt],
                    ),
                )
            }
    }
}
