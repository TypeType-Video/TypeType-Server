package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.models.YoutubeTakeoutPreviewItem
import java.time.Duration

class YoutubeTakeoutImportCache(
    maxPreviewEntries: Int = DEFAULT_MAX_PREVIEW_ENTRIES,
    maxParsedEntries: Int = DEFAULT_MAX_PARSED_ENTRIES,
    ttl: Duration = DEFAULT_TTL,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val previewCache = BoundedExpiringCache<String, YoutubeTakeoutPreviewItem>(
        maxEntries = maxPreviewEntries,
        ttl = ttl,
        clock = clock,
    )
    private val parsedCache = BoundedExpiringCache<String, YoutubeTakeoutParsedData>(
        maxEntries = maxParsedEntries,
        ttl = ttl,
        clock = clock,
    )

    fun getPreview(jobId: String): YoutubeTakeoutPreviewItem? = previewCache.get(jobId)

    fun setPreview(jobId: String, preview: YoutubeTakeoutPreviewItem) {
        previewCache.put(jobId, preview)
    }

    fun getParsed(jobId: String): YoutubeTakeoutParsedData? = parsedCache.get(jobId)

    fun setParsed(jobId: String, parsed: YoutubeTakeoutParsedData) {
        parsedCache.put(jobId, parsed)
    }

    fun remove(jobId: String) {
        previewCache.remove(jobId)
        parsedCache.remove(jobId)
    }

    private companion object {
        const val DEFAULT_MAX_PREVIEW_ENTRIES = 256
        const val DEFAULT_MAX_PARSED_ENTRIES = 1
        val DEFAULT_TTL: Duration = Duration.ofMinutes(30)
    }
}
