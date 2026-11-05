package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseBilibiliMirrorProbingMigration {
    fun apply() {
        TransactionManager.current().exec(
            "ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS bilibili_mirror_probing_enabled BOOLEAN NOT NULL DEFAULT FALSE",
        )
    }
}
