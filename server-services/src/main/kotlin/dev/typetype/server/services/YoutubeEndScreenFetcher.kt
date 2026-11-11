package dev.typetype.server.services

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import dev.typetype.server.models.EndScreenItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

/**
 * Reads creator end screens for a playback served from a token session. Only the WEB player response
 * carries them, and YouTube answers it for the session visitor where an anonymous WEB request is challenged.
 */
class YoutubeEndScreenFetcher {
    suspend fun fetch(videoId: String, visitorData: String): List<EndScreenItem> = withContext(Dispatchers.IO) {
        runCatching { parseEndScreenResponse(videoId, request(videoId, visitorData)) }.getOrDefault(emptyList())
    }

    private fun request(videoId: String, visitorData: String): String {
        val localization = NewPipe.getPreferredLocalization()
        val body = YoutubeParsingHelper.prepareDesktopJsonBuilder(localization, NewPipe.getPreferredContentCountry())
            .value("videoId", videoId)
            .value("contentCheckOk", true)
            .value("racyCheckOk", true)
            .done()
        body.getObject("context").getObject("client")["visitorData"] = visitorData
        runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull()?.let { timestamp ->
            body["playbackContext"] = JsonObject(mapOf("contentPlaybackContext" to JsonObject(mapOf("signatureTimestamp" to timestamp))))
        }
        val headers = HashMap<String, List<String>>()
        YoutubeParsingHelper.addYoutubeHeaders(headers)
        headers["Content-Type"] = listOf("application/json")
        headers["X-Goog-Visitor-Id"] = listOf(visitorData)
        val response = NewPipe.getDownloader().post(PLAYER_URL, headers, JsonWriter.string(body).toByteArray(), localization)
        return YoutubeParsingHelper.getValidJsonResponseBody(response)
    }
}

internal fun parseEndScreenResponse(videoId: String, body: String): List<EndScreenItem> {
    val player = JsonParser.`object`().from(body)
    if (player.getObject("videoDetails").getString("videoId") != videoId) return emptyList()
    return YoutubeStreamExtractor.parseEndScreenElements(player.getObject("endscreen")).toEndScreenItems()
}

private const val PLAYER_URL = YoutubeParsingHelper.YOUTUBEI_V1_URL + "player?" +
    YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER + "&\$fields=videoDetails,endscreen"
