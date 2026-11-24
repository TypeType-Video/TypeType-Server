package dev.typetype.server.db

import dev.typetype.server.db.tables.FocusAiReviewsTable
import dev.typetype.server.db.tables.FocusAllowOnceGrantsTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseFocusMigration {
    fun apply(): Unit {
        SchemaUtils.create(FocusAllowOnceGrantsTable, FocusAiReviewsTable)
        exec("ALTER TABLE focus_allow_once_grants ADD COLUMN IF NOT EXISTS grant_kind TEXT NOT NULL DEFAULT 'outside_group'")
        exec("ALTER TABLE focus_allow_once_grants ADD COLUMN IF NOT EXISTS grant_source TEXT NOT NULL DEFAULT 'captcha'")
        exec("ALTER TABLE focus_allow_once_grants ADD COLUMN IF NOT EXISTS unlock_at BIGINT NOT NULL DEFAULT 0")
        exec("CREATE INDEX IF NOT EXISTS idx_focus_allow_once_user_day ON focus_allow_once_grants (user_id, day_key)")
        exec("CREATE INDEX IF NOT EXISTS idx_focus_allow_once_user_day_kind ON focus_allow_once_grants (user_id, day_key, grant_kind, grant_source)")
        exec("CREATE INDEX IF NOT EXISTS idx_focus_allow_once_user_expires ON focus_allow_once_grants (user_id, expires_at)")
        exec("CREATE INDEX IF NOT EXISTS idx_focus_ai_reviews_user_video ON focus_ai_reviews (user_id, video_key, rejection_expires_at)")
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
