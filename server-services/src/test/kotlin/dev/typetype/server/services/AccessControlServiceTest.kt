package dev.typetype.server.services

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.AllowedPlaylistItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessControlServiceTest {
    private val settingsService: SettingsService = mockk()
    private val allowedChannelsService: AllowedChannelsService = mockk()
    private val allowedPlaylistsService: AllowedPlaylistsService = mockk()
    private val adminSettingsService: AdminSettingsService = mockk()
    private val service = AccessControlService(
        settingsService = settingsService,
        allowedChannelsService = allowedChannelsService,
        allowedPlaylistsService = allowedPlaylistsService,
        adminSettingsService = adminSettingsService,
    )

    private val allowedChannel = AllowedChannelItem(url = "https://youtube.com/@allowed", name = "Allowed")
    private val allowedPlaylist = AllowedPlaylistItem(url = "https://youtube.com/playlist?list=allowed")

    private fun stubUserLists(userId: String = "admin-1") {
        coEvery { allowedChannelsService.getChannels(userId) } returns listOf(allowedChannel)
        coEvery { allowedPlaylistsService.getPlaylists(userId) } returns listOf(allowedPlaylist)
    }

    @Test
    fun `admin bypasses parental controls when applyParentalControlsToAdmins is false`() = runBlocking {
        coEvery { adminSettingsService.get() } returns AdminSettingsItem(
            accessMode = ACCESS_MODE_ALLOW_LIST,
            applyParentalControlsToAdmins = false,
        )

        val profile = service.profileFor("admin-1", role = "admin")

        assertFalse(profile.enabled)
    }

    @Test
    fun `admin is restricted by global allow list when applyParentalControlsToAdmins is true`() = runBlocking {
        coEvery { adminSettingsService.get() } returns AdminSettingsItem(
            accessMode = ACCESS_MODE_ALLOW_LIST,
            applyParentalControlsToAdmins = true,
        )
        coEvery { settingsService.getAccessModePolicy("admin-1") } returns AccessModePolicy(
            accessMode = ACCESS_MODE_UNRESTRICTED,
            adminManaged = false,
        )
        stubUserLists("admin-1")

        val profile = service.profileFor("admin-1", role = "admin")

        assertTrue(profile.enabled)
        assertEquals(listOf(allowedChannel), profile.channels)
        assertEquals(listOf(allowedPlaylist), profile.playlists)
    }

    @Test
    fun `admin with explicit unrestricted override bypasses global allow list even when applyParentalControlsToAdmins is true`() = runBlocking {
        coEvery { adminSettingsService.get() } returns AdminSettingsItem(
            accessMode = ACCESS_MODE_ALLOW_LIST,
            applyParentalControlsToAdmins = true,
        )
        coEvery { settingsService.getAccessModePolicy("admin-1") } returns AccessModePolicy(
            accessMode = ACCESS_MODE_UNRESTRICTED,
            adminManaged = true,
        )

        val profile = service.profileFor("admin-1", role = "admin")

        assertFalse(profile.enabled)
    }

    @Test
    fun `admin with user allow list is restricted when global is unrestricted and applyParentalControlsToAdmins is true`() = runBlocking {
        coEvery { adminSettingsService.get() } returns AdminSettingsItem(
            accessMode = ACCESS_MODE_UNRESTRICTED,
            applyParentalControlsToAdmins = true,
        )
        coEvery { settingsService.getAccessModePolicy("admin-1") } returns AccessModePolicy(
            accessMode = ACCESS_MODE_ALLOW_LIST,
            adminManaged = false,
        )
        stubUserLists("admin-1")

        val profile = service.profileFor("admin-1", role = "admin")

        assertTrue(profile.enabled)
        assertEquals(listOf(allowedChannel), profile.channels)
    }
}
