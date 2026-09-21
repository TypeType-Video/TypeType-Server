package dev.typetype.server

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PipePipeBackupTestFixtures {
    fun createBackupZip(): Path {
        return createBackupZipWithDbEntry("newpipe.db")
    }

    fun createBackupZipWithDbEntry(entryName: String): Path {
        val db = Files.createTempFile("pipepipe-test-", ".db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { sqlite ->
            sqlite.createStatement().use { st ->
                st.execute("CREATE TABLE subscriptions(service_id INTEGER, url TEXT, name TEXT, avatar_url TEXT)")
                st.execute("CREATE TABLE streams(uid INTEGER PRIMARY KEY, url TEXT, title TEXT, duration INTEGER, uploader TEXT, uploader_url TEXT, thumbnail_url TEXT)")
                st.execute("CREATE TABLE stream_history(stream_id INTEGER, access_date INTEGER)")
                st.execute("CREATE TABLE playlists(uid INTEGER PRIMARY KEY, name TEXT, display_index INTEGER)")
                st.execute("CREATE TABLE playlist_stream_join(playlist_id INTEGER, stream_id INTEGER, join_index INTEGER)")
                st.execute("CREATE TABLE stream_state(stream_id INTEGER, progress_time INTEGER)")
                st.execute("CREATE TABLE search_history(search TEXT, creation_date INTEGER)")
                st.execute("INSERT INTO subscriptions VALUES (0, 'https://youtube.com/@x', 'X', 'https://img/x.jpg')")
                st.execute("INSERT INTO streams VALUES (1, 'https://youtube.com/watch?v=1', 'Video 1', 120, 'Chan', 'https://youtube.com/@chan', 'https://img/1.jpg')")
                st.execute("INSERT INTO stream_history VALUES (1, 1000)")
                st.execute("INSERT INTO playlists VALUES (10, 'Fav', 0)")
                st.execute("INSERT INTO playlist_stream_join VALUES (10, 1, 0)")
                st.execute("INSERT INTO stream_state VALUES (1, 42)")
                st.execute("INSERT INTO search_history VALUES ('lofi', 2000)")
            }
        }
        val zip = Files.createTempFile("pipepipe-backup-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry(entryName))
            Files.newInputStream(db).use { input -> input.copyTo(out) }
            out.closeEntry()
        }
        Files.deleteIfExists(db)
        return zip
    }
}
