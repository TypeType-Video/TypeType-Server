package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseYoutubeRemoteLoginMigration {
    fun apply() {
        exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS youtube_remote_login_enabled BOOLEAN NOT NULL DEFAULT false")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
