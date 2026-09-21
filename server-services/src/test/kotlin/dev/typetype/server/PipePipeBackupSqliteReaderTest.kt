package dev.typetype.server

import dev.typetype.server.services.PipePipeBackupSqliteReader
import dev.typetype.server.services.PipePipeBackupZipExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PipePipeBackupSqliteReaderTest {

    @Test
    fun `reader parses expected snapshot`() {
        val zip = PipePipeBackupTestFixtures.createBackupZip()
        val sqlite = PipePipeBackupZipExtractor.extractDatabase(zip)
        val snapshot = PipePipeBackupSqliteReader().read(sqlite)
        Files.deleteIfExists(sqlite)
        Files.deleteIfExists(zip)
        assertEquals(1, snapshot.subscriptions.size)
        assertEquals(1, snapshot.history.size)
        assertEquals(1, snapshot.playlists.size)
        assertEquals(1, snapshot.progress.size)
        assertEquals(1, snapshot.searchHistory.size)
        assertEquals("https://youtube.com/watch?v=1", snapshot.history.first().url)
    }

    @Test
    fun `reader accepts pipepipe db filename`() {
        val zip = PipePipeBackupTestFixtures.createBackupZipWithDbEntry("pipepipe.db")
        val sqlite = PipePipeBackupZipExtractor.extractDatabase(zip)
        val snapshot = PipePipeBackupSqliteReader().read(sqlite)
        Files.deleteIfExists(sqlite)
        Files.deleteIfExists(zip)
        assertEquals(1, snapshot.history.size)
    }

    @Test
    fun `reader falls back to streams history and progress fields`() {
        val db = Files.createTempFile("pipepipe-streams-fallback-", ".db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:$db").use { sqlite ->
            sqlite.createStatement().use { st ->
                st.execute("CREATE TABLE streams(uid INTEGER PRIMARY KEY, url TEXT, title TEXT, duration INTEGER, uploader TEXT, uploader_url TEXT, thumbnail_url TEXT, last_access_date INTEGER, progress_time INTEGER)")
                st.execute("INSERT INTO streams VALUES (1, 'https://youtube.com/watch?v=fallback', 'Fallback', 99, 'Uploader', 'https://youtube.com/@uploader', 'https://img/fallback.jpg', 777, 33)")
            }
        }
        val snapshot = PipePipeBackupSqliteReader().read(db)
        Files.deleteIfExists(db)
        assertEquals(1, snapshot.history.size)
        assertEquals(1, snapshot.progress.size)
        assertEquals("https://youtube.com/watch?v=fallback", snapshot.history.first().url)
    }
}
