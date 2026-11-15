package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseApplyParentalControlsToAdminsMigration {
    fun apply() {
        TransactionManager.current().exec(
            "ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS apply_parental_controls_to_admins BOOLEAN NOT NULL DEFAULT FALSE",
        )
    }
}
