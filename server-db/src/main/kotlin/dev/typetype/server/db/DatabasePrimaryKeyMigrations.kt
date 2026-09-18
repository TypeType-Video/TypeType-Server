package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabasePrimaryKeyMigrations {
    fun apply() {
        recreatePrimaryKey(table = "subscriptions", keyName = "subscriptions_pkey", columns = "user_id, channel_url")
        recreatePrimaryKey(table = "progress", keyName = "progress_pkey", columns = "user_id, video_url")
        recreatePrimaryKey(table = "favorites", keyName = "favorites_pkey", columns = "user_id, video_url")
        recreatePrimaryKey(table = "watch_later", keyName = "watch_later_pkey", columns = "user_id, url")
        recreatePrimaryKey(table = "blocked_channels", keyName = "blocked_channels_pkey", columns = "user_id, channel_url")
        recreatePrimaryKey(table = "blocked_keywords", keyName = "blocked_keywords_pkey", columns = "user_id, keyword")
        recreatePrimaryKey(table = "blocked_videos", keyName = "blocked_videos_pkey", columns = "user_id, video_url")
        recreatePrimaryKey(table = "settings", keyName = "settings_pkey", columns = "user_id")
    }

    private fun recreatePrimaryKey(table: String, keyName: String, columns: String) {
        val sql = """
            DO $$
            DECLARE current_name text;
            DECLARE current_cols text;
            BEGIN
                SELECT c.conname, string_agg(a.attname, ', ' ORDER BY array_position(i.indkey, a.attnum))
                INTO current_name, current_cols
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_index i ON i.indrelid = t.oid AND i.indisprimary
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(i.indkey)
                WHERE t.relname = '$table' AND c.contype = 'p'
                GROUP BY c.conname;
                IF current_cols IS DISTINCT FROM '$columns' THEN
                    IF current_name IS NOT NULL THEN
                        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', '$table', current_name);
                    END IF;
                    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I PRIMARY KEY ($columns)', '$table', '$keyName');
                END IF;
            END $$;
        """.trimIndent()
        exec(sql)
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
