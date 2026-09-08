package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseProfileAccountsMigration {
    fun apply() {
        exec(
            """
            INSERT INTO profile_accounts (profile_id, owner_user_id, display_name, is_default, last_used_at, created_at, updated_at)
            SELECT id, id, COALESCE(NULLIF(name, ''), 'Profile'), TRUE, 0, created_at, updated_at
            FROM users
            ON CONFLICT (profile_id) DO NOTHING
            """.trimIndent(),
        )
        exec("CREATE INDEX IF NOT EXISTS profile_accounts_owner_idx ON profile_accounts (owner_user_id)")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
