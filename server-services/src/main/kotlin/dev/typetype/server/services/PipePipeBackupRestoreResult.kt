package dev.typetype.server.services

import dev.typetype.server.models.RestorePipePipeResultItem

data class PipePipeBackupRestoreResult(
    val counts: RestorePipePipeResultItem,
    val historyMinWatchedAt: Long,
    val historyMaxWatchedAt: Long,
)
