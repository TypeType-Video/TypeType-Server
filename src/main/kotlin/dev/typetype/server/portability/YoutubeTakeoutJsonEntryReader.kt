package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken

internal object YoutubeTakeoutJsonEntryReader {
    fun read(parser: JsonParser): YoutubeTakeoutJsonEntry {
        var title = ""
        var titleUrl = ""
        var time = ""
        var description = ""
        var header = ""
        val controls = mutableListOf<String>()
        val products = mutableListOf<String>()
        val subtitles = mutableListOf<YoutubeTakeoutJsonSubtitle>()
        val details = mutableListOf<String>()
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val name = parser.currentName()
            when (parser.nextToken()) {
                JsonToken.VALUE_STRING, JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> when (name) {
                    "title" -> title = parser.textOrEmpty()
                    "titleUrl" -> titleUrl = parser.textOrEmpty()
                    "time", "publishedAt" -> time = parser.textOrEmpty()
                    "description" -> description = parser.textOrEmpty()
                    "header" -> header = parser.textOrEmpty()
                }
                JsonToken.START_OBJECT -> if (name == "snippet") {
                    val nested = read(parser)
                    if (title.isBlank()) title = nested.title
                    if (titleUrl.isBlank()) titleUrl = nested.titleUrl
                    if (time.isBlank()) time = nested.time
                    if (description.isBlank()) description = nested.description
                    if (header.isBlank()) header = nested.header
                    controls += nested.controls
                    products += nested.products
                    subtitles += nested.subtitles
                    details += nested.details
                } else parser.skipChildren()
                JsonToken.START_ARRAY -> when (name) {
                    "activityControls" -> readStrings(parser, controls)
                    "products" -> readStrings(parser, products)
                    "subtitles" -> readSubtitles(parser, subtitles)
                    "details" -> readDetails(parser, details)
                    else -> parser.skipChildren()
                }
                else -> parser.skipChildren()
            }
        }
        return YoutubeTakeoutJsonEntry(title, titleUrl, time, description, header, controls, products, subtitles, details)
    }

    private fun readStrings(parser: JsonParser, target: MutableList<String>) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) target += parser.textOrEmpty()
            else parser.skipChildren()
        }
    }

    private fun readSubtitles(parser: JsonParser, target: MutableList<YoutubeTakeoutJsonSubtitle>) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren()
                continue
            }
            var name = ""
            var url = ""
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val field = parser.currentName()
                when (parser.nextToken()) {
                    JsonToken.VALUE_STRING -> when (field) {
                        "name" -> name = parser.textOrEmpty()
                        "url" -> url = parser.textOrEmpty()
                    }
                    else -> parser.skipChildren()
                }
            }
            target += YoutubeTakeoutJsonSubtitle(name, url)
        }
    }

    private fun readDetails(parser: JsonParser, target: MutableList<String>) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren()
                continue
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val field = parser.currentName()
                if (parser.nextToken() == JsonToken.VALUE_STRING && field == "name") target += parser.textOrEmpty()
                else parser.skipChildren()
            }
        }
    }
}

internal data class YoutubeTakeoutJsonEntry(
    val title: String,
    val titleUrl: String,
    val time: String,
    val description: String,
    val header: String,
    val controls: List<String>,
    val products: List<String>,
    val subtitles: List<YoutubeTakeoutJsonSubtitle>,
    val details: List<String>,
)

internal data class YoutubeTakeoutJsonSubtitle(val name: String, val url: String)
