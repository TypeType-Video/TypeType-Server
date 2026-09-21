package dev.typetype.server.services

object YoutubeTakeoutLimits {
    const val MAX_UPLOAD_BYTES: Long = 2L * 1024 * 1024 * 1024
    const val MAX_ZIP_ENTRIES: Int = 10_000
    const val MAX_TMP_BYTES: Long = 4L * 1024 * 1024 * 1024
    const val JOB_TTL_MS: Long = 24L * 60 * 60 * 1000
}
