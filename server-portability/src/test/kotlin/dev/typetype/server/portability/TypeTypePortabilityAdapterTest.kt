package dev.typetype.server.portability

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class TypeTypePortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `detects formatted TypeType backups`() {
        val file = directory.resolve("formatted-typetype.json")
        Files.writeString(
            file,
            """
                {
                  "format": "typetype-backup",
                  "version": 1,
                  "subscriptions": []
                }
            """.trimIndent(),
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")

        assertEquals(PortabilityFormat.TYPE_TYPE, TypeTypePortabilityAdapter().detect(input)?.format)
    }

    @Test
    fun `legacy TypeType backup round trips through canonical records`() {
        val source = PortabilitySpool.create(directory)
        source.write(PortabilitySubscription("https://youtube.com/channel/UC1", "Channel", subscribedAt = 10))
        source.write(PortabilitySubscriptionGroup("News"))
        source.write(PortabilitySubscriptionGroupMembership("News", "https://youtube.com/channel/UC1"))
        source.write(PortabilityPlaylist("playlist-1", "Saved", "Description", 20))
        source.write(
            PortabilityPlaylistVideo(
                "playlist-1",
                0,
                PortabilityVideo("https://youtube.com/watch?v=video000001", "Video"),
                30,
            ),
        )
        source.write(PortabilitySettings(buildJsonObject { put("defaultQuality", "720p") }))
        source.write(PortabilityContentFilter("blockedKeyword", "spoiler", createdAt = 40))

        val adapter = TypeTypePortabilityAdapter()
        val output = ByteArrayOutputStream()
        adapter.encode(source, output, source.categories())
        val file = directory.resolve("typetype.json")
        Files.write(file, output.toByteArray())
        val input = PortabilityInputFactory.create(file, "typetype.json", "application/json")
        val restored = PortabilitySpool.create(directory)

        assertEquals(PortabilityFormat.TYPE_TYPE, adapter.detect(input)?.format)
        adapter.decode(input, restored)

        assertEquals(source.counts(), restored.counts())
        assertTrue(output.toString().contains("\"subscriptionGroups\""))
        assertTrue(output.toString().contains("\"blockedKeywords\""))
        restored.delete()
        source.delete()
    }
}
