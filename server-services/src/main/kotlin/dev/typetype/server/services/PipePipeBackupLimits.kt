package dev.typetype.server.services

object PipePipeBackupLimits {
    const val MAX_UPLOAD_BYTES: Long = 32L * 1024 * 1024
    const val MAX_DB_BYTES: Long = 128L * 1024 * 1024
    const val MAX_COMPRESSION_RATIO: Double = 120.0
    const val MAX_ZIP_ENTRIES: Int = 512
}
