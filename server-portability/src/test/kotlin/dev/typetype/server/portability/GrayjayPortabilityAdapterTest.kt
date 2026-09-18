package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

class GrayjayPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `imports current grayjay reconstruction stores`() {
        val archive = directory.resolve("grayjay.zip")
        writeGrayjayFixture(archive)
        val input = PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
        val spool = PortabilitySpool.create(directory)
        val adapter = GrayjayPortabilityAdapter()

        assertEquals("1", requireNotNull(adapter.detect(input)).formatVersion)
        adapter.decode(input, spool)

        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(2L, spool.counts()[PortabilityCategory.SUBSCRIPTION_GROUPS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(1L, spool.counts()[PortabilityCategory.PROGRESS])
        assertEquals(3L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.WATCH_LATER])
        spool.delete()
    }

    @Test
    fun `exports a grayjay archive that round trips through the adapter`() {
        val source = PortabilitySpool.create(directory)
        source.write(PortabilitySubscription("https://www.youtube.com/channel/UC1", "Channel"))
        source.write(PortabilitySubscriptionGroup("News"))
        source.write(PortabilitySubscriptionGroupMembership("News", "https://www.youtube.com/channel/UC1"))
        source.write(PortabilityHistory(PortabilityVideo("https://youtu.be/v1", "Title"), 100, 25))
        source.write(PortabilityPlaylist("p1", "Playlist"))
        source.write(PortabilityPlaylistVideo("p1", 0, PortabilityVideo("https://youtu.be/v1")))
        source.write(PortabilityWatchLater(PortabilityVideo("https://youtu.be/v2")))
        val archive = directory.resolve("export.zip")
        archive.outputStream().use { GrayjayPortabilityAdapter().encode(source, it, source.categories()) }

        ZipFile(archive.toFile()).use { zip ->
            assertTrue(zip.getInputStream(zip.getEntry("exportInfo")).reader().readText().contains("\"version\":\"1\""))
            assertTrue(zip.getInputStream(zip.getEntry("stores/playlists")).reader().readText().contains("Playlist:::p1"))
        }
        val restored = PortabilitySpool.create(directory)
        val input = PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
        GrayjayPortabilityAdapter().decode(input, restored)
        assertEquals(2L, restored.counts()[PortabilityCategory.SUBSCRIPTION_GROUPS])
        assertEquals(2L, restored.counts()[PortabilityCategory.PLAYLISTS])
        source.delete()
        restored.delete()
    }

    private fun writeGrayjayFixture(path: Path) {
        ZipOutputStream(path.outputStream()).use { zip ->
            entry(zip, "exportInfo", "{\"version\":\"1\"}")
            entry(zip, "stores/subscriptions", "[\"https://www.youtube.com/channel/UC1\"]")
            entry(zip, "stores/subscription_groups", "[\"{\\\"name\\\":\\\"News\\\",\\\"urls\\\":[\\\"https://www.youtube.com/channel/UC1\\\"]}\"]")
            entry(zip, "stores/history", "[\"https://youtu.be/v1|||100|||25|||Title\"]")
            entry(zip, "stores/playlists", "[\"Playlist:::p1\\nhttps://youtu.be/v1\\nhttps://youtu.be/v2\"]")
            entry(zip, "stores/watch_later", "[\"https://youtu.be/v2\"]")
            entry(zip, "plugins", "{}")
            entry(zip, "plugin_settings", "{}")
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray())
        zip.closeEntry()
    }
}
