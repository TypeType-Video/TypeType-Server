package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat

internal object SabrDownloadInitialization {
    suspend fun fetch(
        store: SabrSessionStore,
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        val direct = SabrAdaptiveInitialization.fetch(holder, format, store.initCache, DIRECT_TIMEOUT_MS)
            ?: return store.fetchInitializationData(holder, format)
        return direct
    }

    private const val DIRECT_TIMEOUT_MS = 5_000L
}
