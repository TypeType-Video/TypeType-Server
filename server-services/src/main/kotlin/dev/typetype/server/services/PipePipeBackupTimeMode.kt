package dev.typetype.server.services

enum class PipePipeBackupTimeMode(val wireValue: String) {
    RAW("raw"),
    NORMALIZED("normalized"),
    ;

    companion object {
        fun fromQuery(raw: String?): PipePipeBackupTimeMode? {
            if (raw == null) return RAW
            return entries.firstOrNull { it.wireValue == raw.lowercase() }
        }
    }
}
