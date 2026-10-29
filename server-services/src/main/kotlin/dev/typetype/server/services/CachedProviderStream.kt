package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.StreamResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class CachedProviderStream(
    val stream: StreamResponse,
    val alternates: Map<String, List<String>>,
)

internal fun encodeCachedStream(stream: StreamResponse): String =
    if (stream.providerMediaAlternates.isEmpty()) {
        CacheJson.encodeToString(stream)
    } else {
        CacheJson.encodeToString(CachedProviderStream(stream, stream.providerMediaAlternates))
    }

internal fun decodeCachedStream(raw: String): StreamResponse =
    runCatching { CacheJson.decodeFromString<CachedProviderStream>(raw) }
        .map { it.stream.copy(providerMediaAlternates = it.alternates) }
        .getOrElse { CacheJson.decodeFromString<StreamResponse>(raw) }
