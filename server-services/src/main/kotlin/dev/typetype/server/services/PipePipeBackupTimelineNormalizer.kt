package dev.typetype.server.services

object PipePipeBackupTimelineNormalizer {
    fun normalize(items: List<PipePipeBackupHistoryItem>): List<PipePipeBackupHistoryItem> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedBy { it.watchedAt }
        val oldMin = sorted.first().watchedAt
        val oldMax = sorted.last().watchedAt
        if (oldMax <= oldMin) {
            val now = System.currentTimeMillis()
            return sorted.mapIndexed { index, item -> item.copy(watchedAt = now - (sorted.size - index).toLong()) }
                .sortedByDescending { it.watchedAt }
        }
        val window = 30L * 24 * 60 * 60 * 1000
        val newMax = System.currentTimeMillis()
        val newMin = newMax - window
        val scale = (newMax - newMin).toDouble() / (oldMax - oldMin).toDouble()
        return sorted.map { item ->
            val shifted = newMin + ((item.watchedAt - oldMin).toDouble() * scale).toLong()
            item.copy(watchedAt = shifted)
        }.sortedByDescending { it.watchedAt }
    }
}
