package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64

internal class SabrInfoSharedCache(private val cache: CacheService?) {
    suspend fun getPlayback(videoId: String): YoutubeSabrInfo? = get("sabr:info:v2:$videoId")

    suspend fun getInitialization(videoId: String): YoutubeSabrInfo? = get("sabr:init-info:v1:$videoId")

    suspend fun putPlayback(videoId: String, info: YoutubeSabrInfo): Unit = put("sabr:info:v2:$videoId", info)

    suspend fun putInitialization(videoId: String, info: YoutubeSabrInfo): Unit = put("sabr:init-info:v1:$videoId", info)

    suspend fun removePlayback(videoId: String): Unit {
        runCatching { cache?.delete("sabr:info:v2:$videoId") }
    }

    private suspend fun get(key: String): YoutubeSabrInfo? {
        val encoded = runCatching { cache?.get(key) }.getOrNull() ?: return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(encoded)
            ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
                input.readObject() as? YoutubeSabrInfo
            }
        }.getOrNull()
    }

    private suspend fun put(key: String, info: YoutubeSabrInfo): Unit {
        val target = cache ?: return
        val encoded = runCatching {
            val output = ByteArrayOutputStream()
            ObjectOutputStream(output).use { it.writeObject(info) }
            Base64.getEncoder().encodeToString(output.toByteArray())
        }.getOrNull() ?: return
        runCatching { target.set(key, encoded, TTL_SECONDS) }
    }

    private companion object {
        const val TTL_SECONDS = 600L
    }
}
