package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface DeArrowRemote {
    suspend fun branding(videoId: String): String?
    suspend fun thumbnail(videoId: String, timestamp: Double): ByteArray?
}

class DeArrowClient(private val client: OkHttpClient = OkHttpClient()) : DeArrowRemote {
    override suspend fun branding(videoId: String): String? =
        get("https://sponsor.ajay.app/api/branding?videoID=$videoId&fetchAll=true")?.decodeToString()

    override suspend fun thumbnail(videoId: String, timestamp: Double): ByteArray? =
        get("https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$videoId&time=$timestamp")

    private suspend fun get(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.bytes().takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }
}
