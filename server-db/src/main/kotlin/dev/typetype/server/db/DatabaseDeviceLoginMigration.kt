package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseDeviceLoginMigration {
    fun apply() {
        TransactionManager.current()
            .exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS device_login_enabled BOOLEAN NOT NULL DEFAULT TRUE")
    }
}
