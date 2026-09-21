package dev.typetype.server.services

import java.sql.Connection

object PipePipeBackupSqliteMeta {
    fun hasTable(sqlite: Connection, tableName: String): Boolean {
        val sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND lower(name) = lower(?) LIMIT 1"
        return sqlite.prepareStatement(sql).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { rs -> rs.next() }
        }
    }

    fun hasColumn(sqlite: Connection, tableName: String, columnName: String): Boolean {
        if (!hasTable(sqlite, tableName)) return false
        return sqlite.prepareStatement("PRAGMA table_info($tableName)").use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    if (rs.getString("name").equals(columnName, ignoreCase = true)) return true
                }
                false
            }
        }
    }
}
