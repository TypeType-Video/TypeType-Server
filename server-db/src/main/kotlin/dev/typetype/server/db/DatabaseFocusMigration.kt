package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseFocusMigration {
    fun apply(): Unit {
        exec("ALTER TABLE focus_settings ADD COLUMN IF NOT EXISTS study_group_id TEXT")
        exec("ALTER TABLE focus_settings ADD COLUMN IF NOT EXISTS schedule TEXT NOT NULL DEFAULT 'always'")
        exec("ALTER TABLE focus_settings ADD COLUMN IF NOT EXISTS timezone TEXT")
        exec("ALTER TABLE focus_settings ADD COLUMN IF NOT EXISTS strict_blocking BOOLEAN NOT NULL DEFAULT FALSE")
        exec("ALTER TABLE focus_settings ADD COLUMN IF NOT EXISTS grayscale_thumbnails BOOLEAN NOT NULL DEFAULT FALSE")
        exec("ALTER TABLE focus_settings ALTER COLUMN strict_blocking SET DEFAULT FALSE")
        exec("ALTER TABLE focus_settings ALTER COLUMN grayscale_thumbnails SET DEFAULT FALSE")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
