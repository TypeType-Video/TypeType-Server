package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter streams takeout categories and keeps playlist order`() {
        val archive = directory.resolve("takeout.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.entry(
                "Takeout/YouTube and YouTube Music/subscriptions/subscriptions.csv",
                "Channel Id,Channel Url,Channel Title\nUC123456789012,https://youtube.com/channel/UC123456789012,Channel\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/playlists.csv",
                "Playlist ID,Playlist Title\nPL123456789,Imported\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/Videos de Imported.csv",
                "Video ID,Video Title,Video Added Timestamp\nvideo000001,First,2026-01-02T00:00:00Z\nvideo000002,Second,2026-01-01T00:00:00Z\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/Watch later.csv",
                "Video ID,Video Title\nwatch000001,Later\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/Liked videos.csv",
                "Video ID,Video Title\nliked000001,Liked\n",
            )
            output.entry(
                "Takeout/My Activity/YouTube/watch-history.html",
                "You watched <a href=\"https://www.youtube.com/watch?v=seen000001\">Seen</a><br>1 Jan 2026, 12:00:00 CET<br>",
            )
        }
        val input = PortabilityInputFactory.create(archive, "takeout.zip", "application/zip")
        val spool = PortabilitySpool.create(directory)
        val adapter = YoutubeTakeoutPortabilityAdapter()

        assertEquals(PortabilityFormat.YOUTUBE_TAKEOUT, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)

        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(3L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.WATCH_LATER])
        assertEquals(1L, spool.counts()[PortabilityCategory.FAVORITES])
        val positions = mutableListOf<Int>()
        spool.forEachChild(PortabilityCategory.PLAYLISTS, "PL123456789") { record ->
            positions += (record as PortabilityPlaylistVideo).position
        }
        assertEquals(listOf(0, 1), positions)
        assertTrue(spool.issues().isEmpty())
        spool.delete()
    }

    @Test
    fun `adapter detects spanish playlist paths and activity dates`() {
        val archive = directory.resolve("takeout-es.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.entry(
                "Takeout/YouTube y YouTube Music/suscripciones/suscripciones.csv",
                "ID de canal,URL del canal,Título del canal\nUC123456789012,https://youtube.com/channel/UC123456789012,Canal\n",
            )
            output.entry(
                "Takeout/YouTube y YouTube Music/listas de reproducción/catalogo.csv",
                "ID de la lista de reproducción,Título de la lista de reproducción\nPL123456789,Importada\n",
            )
            output.entry(
                "Takeout/YouTube y YouTube Music/listas de reproducción/Videos de Importada.csv",
                "ID de vídeo,Marca de tiempo de creación de la lista de reproducción\nvideo000001,2026-01-02T00:00:00Z\n",
            )
            output.entry(
                "Takeout/YouTube y YouTube Music/listas de reproducción/Ver más tarde.csv",
                "ID de vídeo,Marca de tiempo de creación de la lista de reproducción\nvideo000002,2026-01-01T00:00:00Z\n",
            )
            output.entry(
                "Takeout/Mon actividad/YouTube/watch-history.html",
                "Has visto <a href=\"https://www.youtube.com/watch?v=video000003\">Watched</a><br>16 sept 2026, 18:02:08 CEST<br>",
            )
        }
        val input = PortabilityInputFactory.create(archive, "takeout-es.zip", "application/zip")
        val spool = PortabilitySpool.create(directory)

        YoutubeTakeoutPortabilityAdapter().decode(input, spool)

        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.WATCH_LATER])
        assertEquals(1_789_574_528_000L, (spoolRecord(spool, PortabilityCategory.HISTORY) as PortabilityHistory).watchedAt)
        assertTrue(spool.issues().isEmpty())
        spool.delete()
    }

    @Test
    fun `adapter streams My Activity JSON including embedded URLs`() {
        val json = directory.resolve("watch-history.json")
        Files.writeString(
            json,
            """[
                {"header":"YouTube","title":"Watched First title","titleUrl":"https://www.youtube.com/watch?v=watched01","subtitles":[{"name":"Channel","url":"https://www.youtube.com/channel/UC123456789012"}],"time":"2026-09-16T18:02:08Z","activityControls":["YouTube watch history"]},
                {"header":"YouTube","title":"Liked Second title","titleUrl":"https://www.youtube.com/watch?v=liked01","time":"2026-09-15T18:02:08Z","activityControls":["YouTube watch history"]},
                {"header":"YouTube","title":"Watched URLs://www.youtube.com/watch?v=embedded1","time":"2026-09-14T18:02:08Z","activityControls":["YouTube watch history"]},
                {"header":"YouTube","title":"Titre japonais を視聴しました","titleUrl":"https://music.youtube.com/watch?v=japan01","time":"2026-09-13T18:02:08Z","activityControls":["視聴履歴"]},
                {"header":"YouTube","title":"Visited https://youtu.be/visited1","time":"2026-09-12T18:02:08Z","activityControls":["Web & App Activity","YouTube watch history"]},
                {"header":"YouTube","title":"Watched I liked this title","titleUrl":"https://www.youtube.com/watch?v=falsefav1","time":"2026-09-11T18:02:08Z","activityControls":["YouTube watch history"]},
                {"header":"YouTube","title":"You subscribed to Channel","titleUrl":"https://www.youtube.com/channel/UC987654321098","subtitles":[{"name":"Channel","url":"https://www.youtube.com/channel/UC987654321098"}],"time":"2026-09-10T18:02:08Z","activityControls":["YouTube subscriptions"]},
                {"header":"YouTube","title":"You subscribed to Direct channel","titleUrl":"https://www.youtube.com/@directchannel","time":"2026-09-09T18:02:08Z","activityControls":["YouTube subscriptions"]}
            ]""".trimIndent(),
        )
        val input = PortabilityInputFactory.create(json, json.fileName.toString(), "application/json")
        val spool = PortabilitySpool.create(directory)
        val adapter = YoutubeTakeoutPortabilityAdapter()

        assertEquals(PortabilityFormat.YOUTUBE_TAKEOUT, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)

        assertEquals(5L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(1L, spool.counts()[PortabilityCategory.FAVORITES])
        assertEquals(2L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        val history = mutableListOf<PortabilityHistory>()
        spool.forEach(PortabilityCategory.HISTORY) { history += it as PortabilityHistory }
        assertEquals("https://www.youtube.com/watch?v=embedded1", history[2].video.url)
        assertEquals("YouTube video embedded1", history[2].video.title)
        assertEquals(
            "Titre japonais",
            history.first { it.video.url.contains("japan01") }.video.title,
        )
        assertTrue(spool.issues().isEmpty())
        spool.delete()
    }

    @Test
    fun readsLegacySnippetDatesAndSkipsAdRows() {
        val json = directory.resolve("legacy-watch-history.json")
        Files.writeString(
            json,
            """[
                {"snippet":{"title":"Watched Legacy title","titleUrl":"https://www.youtube.com/watch?v=legacy01","publishedAt":"2026-09-16T18:02:08Z"}},
                {"title":"Watched advertisement","titleUrl":"https://www.youtube.com/watch?v=adrow01","time":"2026-09-16T18:02:08Z","details":[{"name":"Ads"}]}
            ]""".trimIndent(),
        )
        val input = PortabilityInputFactory.create(json, "watch-history.json", "application/json")
        val spool = PortabilitySpool.create(directory)
        try {
            assertEquals(
                PortabilityFormat.YOUTUBE_TAKEOUT,
                requireNotNull(YoutubeTakeoutPortabilityAdapter().detect(input)).format,
            )
            assertNotNull(
                YoutubeTakeoutPortabilityAdapter().detect(
                    PortabilityInputFactory.create(json, "再生履歴.json", "application/json"),
                ),
            )
            YoutubeTakeoutPortabilityAdapter().decode(input, spool)
            assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
            assertTrue(spool.issues().isEmpty())
        } finally {
            spool.delete()
        }
    }

    @Test
    fun `adapter reads JSON activity entries from a Takeout archive`() {
        val archive = directory.resolve("takeout-json.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.entry(
                "Takeout/マイ アクティビティ/再生履歴.json",
                """[{"header":"YouTube","title":"動画 を視聴しました","titleUrl":"https://www.youtube.com/watch?v=archive01","time":"2026-09-16T18:02:08Z"}]""",
            )
        }
        val input = PortabilityInputFactory.create(archive, "takeout-json.zip", "application/zip")
        val spool = PortabilitySpool.create(directory)
        try {
            assertEquals(PortabilityFormat.YOUTUBE_TAKEOUT, requireNotNull(YoutubeTakeoutPortabilityAdapter().detect(input)).format)
            YoutubeTakeoutPortabilityAdapter().decode(input, spool)
            assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        } finally {
            spool.delete()
        }
    }

    private fun spoolRecord(spool: PortabilitySpool, category: PortabilityCategory): PortabilityRecord {
        var result: PortabilityRecord? = null
        spool.forEach(category) { result = it }
        return requireNotNull(result)
    }

    private fun ZipOutputStream.entry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray())
        closeEntry()
    }
}
