package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseOidcMigration {
    fun apply() {
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS oidc_issuer TEXT")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS oidc_subject TEXT")
        exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS local_login_enabled BOOLEAN NOT NULL DEFAULT true")
        exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS oidc_auto_redirect BOOLEAN NOT NULL DEFAULT false")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS users_oidc_identity_unique ON users (oidc_issuer, oidc_subject)")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
