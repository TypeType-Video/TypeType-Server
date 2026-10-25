package dev.typetype.server

import dev.typetype.server.db.DatabasePrimaryKeyMigrations
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class DatabasePrimaryKeyMigrationsTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @Test
    fun `primary key migration only reads constraints from the active schema`() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val activeSchema = "pk_active_$suffix"
        val otherSchema = "pk_other_$suffix"

        transaction {
            TransactionManager.current().exec("CREATE SCHEMA \"$activeSchema\"")
            TransactionManager.current().exec("CREATE SCHEMA \"$otherSchema\"")
        }

        try {
            transaction {
                val tables = listOf(
                    "subscriptions" to "user_id TEXT NOT NULL, channel_url TEXT NOT NULL",
                    "progress" to "user_id TEXT NOT NULL, video_url TEXT NOT NULL",
                    "favorites" to "user_id TEXT NOT NULL, video_url TEXT NOT NULL",
                    "watch_later" to "user_id TEXT NOT NULL, url TEXT NOT NULL",
                    "blocked_channels" to "user_id TEXT NOT NULL, channel_url TEXT NOT NULL",
                    "blocked_keywords" to "user_id TEXT NOT NULL, keyword TEXT NOT NULL",
                    "blocked_videos" to "user_id TEXT NOT NULL, video_url TEXT NOT NULL",
                    "settings" to "user_id TEXT NOT NULL",
                )
                tables.forEach { (table, columns) ->
                    TransactionManager.current().exec(
                        "CREATE TABLE \"$activeSchema\".\"$table\" ($columns)",
                    )
                }
                TransactionManager.current().exec(
                    "CREATE TABLE \"$otherSchema\".favorites " +
                        "(user_id TEXT NOT NULL, video_url TEXT NOT NULL, PRIMARY KEY (video_url))",
                )
                TransactionManager.current().exec("SET LOCAL search_path TO \"$activeSchema\", public")

                DatabasePrimaryKeyMigrations.apply()

                assertEquals("user_id, video_url", primaryKeyColumns(activeSchema, "favorites"))
                assertEquals("video_url", primaryKeyColumns(otherSchema, "favorites"))
            }
        } finally {
            transaction {
                TransactionManager.current().exec("DROP SCHEMA IF EXISTS \"$activeSchema\" CASCADE")
                TransactionManager.current().exec("DROP SCHEMA IF EXISTS \"$otherSchema\" CASCADE")
            }
        }
    }

    private fun primaryKeyColumns(schema: String, table: String): String? =
        TransactionManager.current().exec(
            """
                SELECT string_agg(a.attname, ', ' ORDER BY array_position(i.indkey, a.attnum))
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_index i ON i.indrelid = t.oid AND i.indisprimary
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(i.indkey)
                WHERE n.nspname = '$schema' AND t.relname = '$table' AND c.contype = 'p'
                GROUP BY c.conname
            """.trimIndent(),
        ) { result ->
            if (result.next()) result.getString(1) else null
        }
}
