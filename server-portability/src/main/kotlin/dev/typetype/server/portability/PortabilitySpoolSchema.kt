package dev.typetype.server.portability

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

object PortabilitySpoolSchema {
    fun <T> create(directory: Path, factory: (Path, Connection) -> T): T {
        Files.createDirectories(directory)
        val path = Files.createTempFile(directory, "portability-", ".sqlite")
        val connection = DriverManager.getConnection("jdbc:sqlite:$path")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = NORMAL")
            statement.execute("PRAGMA temp_store = MEMORY")
            statement.execute(
                """
                CREATE TABLE records(
                    ordinal INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT NOT NULL,
                    stable_key TEXT NOT NULL,
                    parent_key TEXT,
                    payload TEXT NOT NULL,
                    UNIQUE(category, stable_key)
                )
                """.trimIndent(),
            )
            statement.execute("CREATE INDEX records_parent_idx ON records(category, parent_key, ordinal)")
            statement.execute("CREATE TABLE categories(category TEXT PRIMARY KEY)")
            statement.execute(
                "CREATE TABLE lookups(namespace TEXT NOT NULL, lookup_key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(namespace, lookup_key))",
            )
            statement.execute(
                """
                CREATE TABLE issues(
                    category TEXT NOT NULL,
                    code TEXT NOT NULL,
                    message TEXT NOT NULL,
                    item_count INTEGER NOT NULL,
                    UNIQUE(category, code, message)
                )
                """.trimIndent(),
            )
        }
        connection.autoCommit = false
        connection.commit()
        return factory(path, connection)
    }
}
