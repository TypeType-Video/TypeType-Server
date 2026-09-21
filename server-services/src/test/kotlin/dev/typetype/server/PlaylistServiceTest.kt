package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaylistServiceTest {

    private val service = PlaylistService()
    private val progressService = ProgressService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `create returns playlist with generated id`() = runBlocking {
        val result = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        assertTrue(result.id.isNotEmpty())
        assertEquals("Test", result.name)
    }

    @Test
    fun `getAll returns empty list when no playlists`() = runBlocking {
        assertTrue(service.getAll(TEST_USER_ID).isEmpty())
    }

    @Test
    fun `getAll returns playlist metadata without videos populated`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com", title = "T", thumbnail = "", duration = 100L, channelName = "C", channelUrl = "https://c", channelAvatar = "avatar", viewCount = 123L))
        val all = service.getAll(TEST_USER_ID)
        assertEquals(1, all.size)
        assertEquals(1, all[0].videoCount)
        assertTrue(all[0].videos.isEmpty())
    }

    @Test
    fun `getById returns playlist with videos`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com", title = "T", thumbnail = "", duration = 100L))
        val found = service.getById(TEST_USER_ID, playlist.id)
        assertNotNull(found)
        assertEquals(1, found?.videos?.size)
    }

    @Test
    fun `getById enriches videos with progress`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com/1", title = "V1", thumbnail = "", duration = 100L))
        progressService.upsert(TEST_USER_ID, "https://yt.com/1", 90L)

        val video = service.getById(TEST_USER_ID, playlist.id)?.videos?.single()

        assertEquals(90L, video?.watchPosition)
        assertEquals(true, video?.watched)
        assertTrue((video?.progressUpdatedAt ?: 0L) > 0L)
    }

    @Test
    fun `getById returns null when not found`() = runBlocking {
        assertNull(service.getById(TEST_USER_ID, "nonexistent"))
    }

    @Test
    fun `update returns true and persists changes`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Old"))
        val updated = service.update(TEST_USER_ID, playlist.id, PlaylistItem(name = "New", description = "Desc"))
        assertTrue(updated)
        assertEquals("New", service.getById(TEST_USER_ID, playlist.id)?.name)
    }

    @Test
    fun `delete returns true and removes playlist`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        assertTrue(service.delete(TEST_USER_ID, playlist.id))
        assertNull(service.getById(TEST_USER_ID, playlist.id))
    }

    @Test
    fun `addVideo persists video with correct position`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        val v1 = service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com/1", title = "V1", thumbnail = "", duration = 10L))
        val v2 = service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com/2", title = "V2", thumbnail = "", duration = 20L))
        assertEquals(0, v1.position)
        assertEquals(1, v2.position)
    }

    @Test
    fun `removeVideo returns true and removes only that video`() = runBlocking {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Test"))
        service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com/1", title = "V1", thumbnail = "", duration = 10L))
        service.addVideo(TEST_USER_ID, playlist.id, PlaylistVideoItem(url = "https://yt.com/2", title = "V2", thumbnail = "", duration = 20L))
        assertTrue(service.removeVideo(TEST_USER_ID, playlist.id, "https://yt.com/1"))
        val videos = service.getById(TEST_USER_ID, playlist.id)?.videos
        assertEquals(1, videos?.size)
        assertEquals("https://yt.com/2", videos?.get(0)?.url)
    }
}
