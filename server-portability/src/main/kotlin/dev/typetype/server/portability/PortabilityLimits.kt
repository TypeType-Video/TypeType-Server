package dev.typetype.server.portability

object PortabilityLimits {
    const val MAX_UPLOAD_BYTES = 512L * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 10_000
    const val MAX_ARCHIVE_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ARCHIVE_ENTRY_BYTES = 512L * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 200L
    const val PROBE_BYTES = 64 * 1024
    const val MAX_RECORDS = 2_000_000L
    const val MAX_RECORD_JSON_BYTES = 2 * 1024 * 1024
    const val MAX_CONTAINER_RECORDS = 100_000
}
