package dev.typetype.server

import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.AllowedPlaylistItem
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.TypeTypeBackupItem
import dev.typetype.server.models.TypeTypeContentFiltersBackup
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.models.BlockedKeywordItem
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SavedPlaylistService
import dev.typetype.server.services.SearchHistoryService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TypeTypeBackupCategory
import dev.typetype.server.services.TypeTypeBackupService
import dev.typetype.server.services.WatchLaterService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TypeTypeBackupServiceTest {
    private val subscriptions = SubscriptionsService()
    private val subscriptionGroups = SubscriptionGroupsService()
    private val history = HistoryService()
    private val playlists = PlaylistService()
    private val watchLater = WatchLaterService()
    private val favorites = FavoritesService()
    private val progress = ProgressService()
    private val searchHistory = SearchHistoryService()
    private val savedPlaylists = SavedPlaylistService()
    private val settings = SettingsService()
    private val blocked = BlockedService()
    private val allowedChannels = AllowedChannelsService()
    private val allowedPlaylists = AllowedPlaylistsService()
    private val service = TypeTypeBackupService(
        subscriptions,
        history,
        playlists,
        watchLater,
        favorites,
        progress,
        searchHistory,
        savedPlaylists,
        settings,
        blocked,
        allowedChannels,
        allowedPlaylists,
    )

    companion object {
        private const val SOURCE = "backup-source"
        private const val TARGET = "backup-target"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `full backup restores every user data category`() = runTest {
        subscriptions.add(SOURCE, SubscriptionItem("https://youtube.com/channel/source", "Source", "avatar"))
        val subscriptionGroup = (
            subscriptionGroups.create(SOURCE, "Favorites") as SubscriptionGroupWriteResult.Success
        ).group
        assertEquals(
            SubscriptionGroupMembershipResult.Success,
            subscriptionGroups.addSubscription(SOURCE, subscriptionGroup.id, "https://youtube.com/channel/source"),
        )
        history.addImported(SOURCE, videoHistory())
        val playlist = playlists.create(SOURCE, PlaylistItem(name = "Saved videos"))
        playlists.addVideo(SOURCE, playlist.id, playlistVideo())
        watchLater.add(SOURCE, watchLaterItem())
        favorites.add(SOURCE, FavoriteItem(videoUrl = VIDEO_URL, title = "Favorite"))
        progress.upsert(SOURCE, VIDEO_URL, 90_000)
        searchHistory.add(SOURCE, "kotlin")
        savedPlaylists.save(
            SOURCE,
            PublicPlaylistItem("PL1", "Public", "https://youtube.com/playlist?list=PL1", "", "Uploader", 3, "playlist"),
        )
        settings.upsert(SOURCE, SettingsItem(defaultPlaybackSpeed = 1.75))
        blocked.addChannel(SOURCE, "https://youtube.com/channel/blocked")
        blocked.addVideo(SOURCE, VIDEO_URL)
        blocked.addKeyword(SOURCE, "spoiler")
        blocked.addKeyword("admin", "global policy", global = true)
        allowedChannels.addChannel(SOURCE, "https://youtube.com/channel/allowed")
        allowedPlaylists.addPlaylist(
            SOURCE,
            AllowedPlaylistItem("https://youtube.com/playlist?list=allowed"),
            global = false,
        )

        val backup = service.export(SOURCE, TypeTypeBackupCategory.all)
        assertFalse(backup.contentFilters.orEmptyKeywords().contains("global policy"))
        assertEquals(90, backup.history?.single()?.progress)
        val result = service.restore(TARGET, backup)

        assertEquals(1, result.restored["subscriptions"])
        assertEquals(1, result.restored["subscriptionGroups"])
        assertEquals(1, result.restored["subscriptionGroupMemberships"])
        val restoredGroup = subscriptionGroups.getAll(TARGET).single()
        assertEquals("Favorites", restoredGroup.name)
        assertEquals(
            listOf("https://youtube.com/channel/source"),
            subscriptionGroups.getChannelUrls(TARGET, restoredGroup.id),
        )
        assertEquals(1, result.restored["history"])
        assertEquals(1, result.restored["playlists"])
        assertEquals(1, result.restored["playlistVideos"])
        assertEquals(1, result.restored["watchLater"])
        assertEquals(1, result.restored["favorites"])
        assertEquals(1, result.restored["progress"])
        assertEquals(1, result.restored["searchHistory"])
        assertEquals(1, result.restored["savedPlaylists"])
        assertEquals(1.75, settings.get(TARGET).defaultPlaybackSpeed)
        assertEquals(1, playlists.getById(TARGET, playlists.getAll(TARGET).single().id)?.videos?.size)
        assertEquals(setOf("spoiler", "global policy"), blocked.getKeywords(TARGET).map { it.keyword }.toSet())
    }

    @Test
    fun `selective restore leaves categories absent from backup untouched`() = runTest {
        favorites.add(TARGET, FavoriteItem(videoUrl = "existing"))
        subscriptions.add(SOURCE, SubscriptionItem("https://youtube.com/channel/source", "Source", ""))
        val backup = service.export(SOURCE, setOf(TypeTypeBackupCategory.SUBSCRIPTIONS))

        service.restore(TARGET, backup)

        assertEquals(1, subscriptions.getAll(TARGET).size)
        assertEquals("existing", favorites.getAll(TARGET).single().videoUrl)
        assertTrue(backup.history == null)
    }

    @Test
    fun `legacy subscription backup without groups preserves compatible memberships`() = runTest {
        subscriptions.add(TARGET, SubscriptionItem("https://youtube.com/channel/keep", "Keep", ""))
        subscriptions.add(TARGET, SubscriptionItem("https://youtube.com/channel/drop", "Drop", ""))
        val group = (subscriptionGroups.create(TARGET, "Existing") as SubscriptionGroupWriteResult.Success).group
        subscriptionGroups.addSubscription(TARGET, group.id, "https://youtube.com/channel/keep")
        subscriptionGroups.addSubscription(TARGET, group.id, "https://youtube.com/channel/drop")
        val legacyBackup = TypeTypeBackupItem(
            exportedAt = 1,
            categories = listOf(TypeTypeBackupCategory.SUBSCRIPTIONS.wireName),
            subscriptions = listOf(
                SubscriptionItem("https://youtube.com/channel/keep", "Keep", "", subscribedAt = 1),
            ),
        )

        service.restore(TARGET, legacyBackup)

        val preservedGroup = subscriptionGroups.getAll(TARGET).single()
        assertEquals(group.id, preservedGroup.id)
        assertEquals(
            listOf("https://youtube.com/channel/keep"),
            subscriptionGroups.getChannelUrls(TARGET, preservedGroup.id),
        )
    }

    @Test
    fun `restore rejects empty normalized blocked keywords`() = runTest {
        val backup = TypeTypeBackupItem(
            exportedAt = 1,
            categories = listOf(TypeTypeBackupCategory.CONTENT_FILTERS.wireName),
            contentFilters = TypeTypeContentFiltersBackup(
                blockedKeywords = listOf(BlockedKeywordItem("   ", 1)),
            ),
        )

        val error = runCatching { service.restore(TARGET, backup) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun videoHistory() = HistoryItem(
        url = VIDEO_URL,
        title = "Video",
        thumbnail = "",
        channelName = "Channel",
        channelUrl = "https://youtube.com/channel/source",
        duration = 120,
        progress = 42,
        watchedAt = 1234,
    )

    private fun playlistVideo() = PlaylistVideoItem(
        url = VIDEO_URL,
        title = "Video",
        thumbnail = "",
        duration = 120,
    )

    private fun watchLaterItem() = WatchLaterItem(
        url = VIDEO_URL,
        title = "Video",
        thumbnail = "",
        duration = 120,
    )

    private fun dev.typetype.server.models.TypeTypeContentFiltersBackup?.orEmptyKeywords() =
        this?.blockedKeywords?.map { it.keyword }.orEmpty()
}

private const val VIDEO_URL = "https://youtube.com/watch?v=backup"
