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
        exec(
            """
            WITH ranked AS (
                SELECT profile_id,
                       row_number() OVER (
                           PARTITION BY owner_user_id
                           ORDER BY is_default DESC, last_used_at DESC, created_at ASC, profile_id ASC
                       ) AS profile_rank
                FROM profile_accounts
            )
            UPDATE profile_accounts AS profiles
            SET is_default = (ranked.profile_rank = 1)
            FROM ranked
            WHERE profiles.profile_id = ranked.profile_id
            """.trimIndent(),
        )
        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS profile_accounts_one_default_idx " +
                "ON profile_accounts (owner_user_id) WHERE is_default",
        )
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
