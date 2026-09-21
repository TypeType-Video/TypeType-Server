package dev.typetype.server.services

object PipePipeBackupSnapshotTimeMode {
    fun apply(snapshot: PipePipeBackupSnapshotItem, mode: PipePipeBackupTimeMode): PipePipeBackupSnapshotItem {
        val history = if (mode == PipePipeBackupTimeMode.NORMALIZED) {
            PipePipeBackupTimelineNormalizer.normalize(snapshot.history)
        } else {
            snapshot.history
        }
        return snapshot.copy(history = history)
    }
}
