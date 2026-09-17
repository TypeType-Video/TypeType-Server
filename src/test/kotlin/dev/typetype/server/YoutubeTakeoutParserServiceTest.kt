package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutParserService
import dev.typetype.server.services.YoutubeTakeoutDateParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutParserServiceTest {
    @Test
    fun `parse detects history watch later and liked videos`() {
        val zip = createZip()
        val parsed = YoutubeTakeoutParserService().parse(zip)
        assertEquals(1, parsed.history.size)
        assertTrue(parsed.history.first().url.contains("watch?v=abc123"))
        assertEquals(1, parsed.watchLater.size)
        assertTrue(parsed.watchLater.first().url.contains("watch?v=watch456"))
        assertEquals(listOf("https://www.youtube.com/watch?v=like789"), parsed.favorites.map { it.videoUrl })
        assertEquals(1_758_045_443_000L, parsed.favorites.first().favoritedAt)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse detects subscriptions without channel url`() {
        val zip = Files.createTempFile("yt-takeout-subscriptions-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube and YouTube Music/subscriptions/subscriptions.csv"))
            out.write("channel id,channel title\nUC1,Linus Tech Tips\n".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(1, parsed.subscriptions.size)
        assertEquals("https://www.youtube.com/channel/UC1", parsed.subscriptions.first().channelUrl)
        assertEquals("Linus Tech Tips", parsed.subscriptions.first().name)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse detects localized french takeout csv files`() {
        val zip = Files.createTempFile("yt-takeout-french-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/abonnements/abonnements.csv"))
            out.write("ID des chaînes,URL des chaînes,Titres des chaînes\nUC1,https://www.youtube.com/channel/UC1,Channel\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/playlists.csv"))
            out.write("ID de la playlist,Titre (d'origine) de la playlist\nPL1,a\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/Vidéos de a.csv"))
            out.write("ID vidéo,Code temporel de création de la vidéo de la playlist\nabc123,2026-01-01T00:00:00+00:00\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/Vidéos de Watch later.csv"))
            out.write("ID vidéo,Code temporel de création de la vidéo de la playlist\nwatch456,2026-01-01T00:00:00+00:00\n".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(1, parsed.subscriptions.size)
        assertEquals(1, parsed.playlists.size)
        assertEquals(1, parsed.playlistItems["a"]?.size)
        assertEquals("YouTube video abc123", parsed.playlistItems["a"]?.first()?.title)
        assertEquals(1_767_225_600_000L, parsed.playlistItems["a"]?.first()?.addedAt)
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", parsed.playlistItems["a"]?.first()?.thumbnail)
        assertEquals(1, parsed.watchLater.size)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse detects spanish takeout dates and playlist names`() {
        val zip = Files.createTempFile("yt-takeout-spanish-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube y YouTube Music/suscripciones/suscripciones.csv"))
            out.write("ID de canal,URL del canal,Título del canal\nUC123456789012,https://www.youtube.com/channel/UC123456789012,Canal\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube y YouTube Music/listas de reproducción/listas de reproducción.csv"))
            out.write("ID de la lista de reproducción,Título de la lista de reproducción\nPL123456789,Importada\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube y YouTube Music/listas de reproducción/Videos de Importada.csv"))
            out.write("ID de vídeo,Marca de tiempo de creación de la lista de reproducción\nvideo000001,2026-01-02T00:00:00Z\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube y YouTube Music/listas de reproducción/Ver más tarde.csv"))
            out.write("ID de vídeo,Marca de tiempo de creación de la lista de reproducción\nvideo000002,2026-01-01T00:00:00Z\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/Mon actividad/YouTube/watch-history.html"))
            out.write("Has visto <a href=\"https://www.youtube.com/watch?v=video000003\">Watched</a><br>16 sept 2026, 18:02:08 CEST<br>".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(1, parsed.subscriptions.size)
        assertEquals(1, parsed.playlists.size)
        assertEquals(1, parsed.playlistItems["Importada"]?.size)
        assertEquals(1, parsed.watchLater.size)
        assertEquals(1, parsed.history.size)
        assertEquals(1_789_574_528_000L, parsed.history.single().watchedAt)
        assertEquals(1_789_574_528_000L, YoutubeTakeoutDateParser.parseEpochMillis("16 septiembre 2026, 18:02:08 CEST"))
        assertEquals(1_789_574_528_000L, YoutubeTakeoutDateParser.parseEpochMillis("16 de septiembre de 2026, 18:02:08 CEST"))
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse skips history rows with an unknown activity date`() {
        val zip = Files.createTempFile("yt-takeout-invalid-date-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/My Activity/YouTube/watch-history.html"))
            out.write("You watched <a href=\"https://www.youtube.com/watch?v=unknown1\">Unknown date</a><br>16 Foo 2026, 12:00:00 CET<br>".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertTrue(parsed.history.isEmpty())
        assertTrue(parsed.history.none { it.watchedAt == 0L })
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse accepts a localized history file without an english activity marker`() {
        val zip = Files.createTempFile("yt-takeout-localized-history-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube/история просмотров/история просмотров.html"))
            out.write(
                ("<a href=\"https://www.youtube.com/watch?v=localized1\">Localized title</a><br>" +
                    "<a href=\"https://www.youtube.com/channel/UC1234567890\">Channel</a><br>" +
                    "16. September 2026, 18:02:08 CEST<br>").toByteArray(),
            )
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(listOf("localized1"), parsed.history.map { it.url.substringAfter("v=") })
        assertTrue(parsed.history.none { it.watchedAt == 0L })
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse preserves takeout playlist order and added dates`() {
        val zip = Files.createTempFile("yt-takeout-playlist-order-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/playlists.csv"))
            out.write("ID de la playlist,Titre (d'origine) de la playlist\nPL123456,Imported\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/Imported.csv"))
            out.write(
                """
                ID vidéo,Code temporel de création de la vidéo de la playlist
                newer000001,2026-01-02T00:00:00+00:00
                older000001,2026-01-01T00:00:00+00:00
                """.trimIndent().plus("\n").toByteArray(),
            )
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)
        val imported = parsed.playlistItems.values.single()

        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=newer000001",
                "https://www.youtube.com/watch?v=older000001",
            ),
            imported.map { it.url },
        )
        assertEquals(listOf(1_767_312_000_000L, 1_767_225_600_000L), imported.map { it.addedAt })
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse preserves favorite dates from video added timestamp header`() {
        val zip = Files.createTempFile("yt-takeout-favorite-order-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube/playlists/Liked videos.csv"))
            out.write(
                "Video ID,Video Added Timestamp\nnewer000001,2026-01-02T00:00:00Z\nolder000001,2026-01-01T00:00:00Z\n"
                    .toByteArray(),
            )
            out.closeEntry()
        }

        val favorites = YoutubeTakeoutParserService().parse(zip).favorites

        assertEquals(listOf("newer000001", "older000001"), favorites.map { it.videoUrl.substringAfter("v=") })
        assertEquals(listOf(1_767_312_000_000L, 1_767_225_600_000L), favorites.map { it.favoritedAt })
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse preserves favorite dates from a localized timestamp header`() {
        val zip = Files.createTempFile("yt-takeout-localized-favorite-order-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube/playlists/Liked videos.csv"))
            out.write("Video ID,Ajoutee a la playlist\nvideo000001,2026-01-03T00:00:00Z\n".toByteArray())
            out.closeEntry()
        }

        val favorite = YoutubeTakeoutParserService().parse(zip).favorites.single()

        assertEquals("video000001", favorite.videoUrl.substringAfter("v="))
        assertEquals(1_767_398_400_000L, favorite.favoritedAt)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse omits unavailable videos from takeout collections and activity`() {
        val zip = Files.createTempFile("yt-takeout-unavailable-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.writeEntry(
                "Takeout/YouTube/playlists/playlists.csv",
                "Playlist ID,Playlist Title\nPL123456,Imported\n",
            )
            out.writeEntry(
                "Takeout/YouTube/playlists/Imported.csv",
                "Video ID,Video Title\nkeep000001,Available title\ngone000001,Deleted video\n",
            )
            out.writeEntry(
                "Takeout/YouTube/playlists/Watch later.csv",
                "Video ID,Video Title\nkeep000002,Another title\ngone000002,Vidéo privée\n",
            )
            out.writeEntry(
                "Takeout/YouTube/playlists/Liked videos.csv",
                "Video ID,Video Title\nkeep000003,Liked title\ngone000003,Video no disponible\n",
            )
            out.writeEntry(
                "Takeout/My Activity/YouTube/watch-history.html",
                """
                You watched <a href="https://www.youtube.com/watch?v=keep000004">Watched title</a><br>
                1 Jan 2026, 12:00:00 CET<br>
                You watched <a href="https://www.youtube.com/watch?v=gone000004">Video unavailable</a><br>
                1 Jan 2026, 13:00:00 CET<br>
                """.trimIndent(),
            )
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(listOf("keep000001"), parsed.playlistItems["Imported"]?.map { it.url.substringAfter("v=") })
        assertEquals(listOf("keep000002"), parsed.watchLater.map { it.url.substringAfter("v=") })
        assertEquals(listOf("keep000003"), parsed.favorites.map { it.videoUrl.substringAfter("v=") })
        assertEquals(listOf("keep000004"), parsed.history.map { it.url.substringAfter("v=") })
        assertTrue(parsed.errors.isEmpty())
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse preserves live video history urls`() {
        val zip = Files.createTempFile("yt-takeout-live-history-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.writeEntry(
                "Takeout/My Activity/YouTube/watch-history.html",
                "You watched <a href=\"https://www.youtube.com/live/live000001\">Live stream</a><br>" +
                    "1 Jan 2026, 12:00:00 CET<br>",
            )
        }

        val history = YoutubeTakeoutParserService().parse(zip).history

        assertEquals("https://www.youtube.com/live/live000001", history.single().url)
        assertEquals("https://i.ytimg.com/vi/live000001/hqdefault.jpg", history.single().thumbnail)
        Files.deleteIfExists(zip)
    }

    private fun createZip(): Path {
        val zip = Files.createTempFile("yt-takeout-parser-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/Mon activité/YouTube/MonActivité.html"))
            out.write("""
                <html><body>
                You watched <a href="https://www.youtube.com/watch?v=abc123">Title</a><br>
                <a href="https://www.youtube.com/channel/UC1">Channel</a><br>
                22 Mar 2026, 19:27:08 CET<br>
                </body></html>
            """.trimIndent().toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube/playlists/Watch later.csv"))
            out.write("video id,added at\nwatch456,2025-09-16T17:57:23+00:00\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube/playlists/Liked videos.csv"))
            out.write("video id,added at\nlike789,2025-09-16T17:57:23+00:00\n".toByteArray())
            out.closeEntry()
        }
        return zip
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray())
        closeEntry()
    }
}
