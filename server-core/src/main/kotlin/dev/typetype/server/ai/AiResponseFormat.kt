package dev.typetype.server.ai

import kotlinx.serialization.json.JsonObject

sealed interface AiResponseFormat {
    data object Text : AiResponseFormat

    data class Json(
        val schema: JsonObject? = null,
    ) : AiResponseFormat
}
