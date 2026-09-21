package dev.typetype.server.services

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptDecoder

class TypetypeTokenYoutubeJavaScriptDecoder(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) : YoutubeJavaScriptDecoder {
    override fun getPlayerData(videoId: String): YoutubeJavaScriptDecoder.PlayerData {
        val json = callDecoder(JSONObject())
        val playerId = json.optString("playerId")
        val signatureTimestamp = json.optInt("signatureTimestamp", -1)
        if (playerId.isBlank() || signatureTimestamp < 0) {
            throw ParsingException("TypeType token decoder returned incomplete player data")
        }
        return YoutubeJavaScriptDecoder.PlayerData(playerId, signatureTimestamp)
    }

    override fun decodeBatch(
        playerId: String,
        signatures: MutableList<String>?,
        throttlingParameters: MutableList<String>?,
    ): YoutubeApiDecoder.BatchDecodeResult {
        val body = JSONObject()
            .put("playerId", playerId)
            .put("signatures", JSONArray(signatures ?: emptyList<String>()))
            .put("throttlingParameters", JSONArray(throttlingParameters ?: emptyList<String>()))
        val json = callDecoder(body)
        return YoutubeApiDecoder.BatchDecodeResult(
            json.optJSONObject("signatures").toMapString(),
            json.optJSONObject("throttlingParameters").toMapString(),
        )
    }

    private fun callDecoder(body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("${tokenServiceUrl.trimEnd('/')}/youtube/player/decoder")
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw ParsingException("TypeType token decoder HTTP ${response.code}: $text")
                }
                return JSONObject(text)
            }
        } catch (error: ParsingException) {
            throw error
        } catch (error: Exception) {
            throw ParsingException("TypeType token decoder failed", error)
        }
    }

    private fun JSONObject?.toMapString(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (key in keySet()) {
            val value = optString(key)
            if (value.isNotBlank()) map[key] = value
        }
        return map
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
