package dev.typetype.server.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object YoutubeRemoteBrowserMessageGuard {
    private val json = Json { ignoreUnknownKeys = true }
    private val clientTypes = setOf("resize", "pointer", "wheel", "key", "text", "cancel")
    private val tokenTypes = setOf("status", "error", "log")
    private val phases = setOf("opening", "awaiting_login", "capturing_session", "connected")
    private val pointerEvents = setOf("down", "up", "move")
    private val keyEvents = setOf("down", "up")
    private val buttons = setOf("left", "middle", "right")

    fun frontendText(text: String, maxBytes: Int): String? {
        if (text.toByteArray(Charsets.UTF_8).size > maxBytes) return null
        val obj = parseObject(text) ?: return null
        return if (isValidClientMessage(obj)) text else null
    }

    fun tokenText(text: String): String? {
        if (text.toByteArray(Charsets.UTF_8).size > 4096) return null
        val obj = parseObject(text) ?: return null
        return if (isValidTokenMessage(obj)) text else null
    }

    private fun isValidClientMessage(obj: JsonObject): Boolean =
        when (obj.string("type")?.takeIf { it in clientTypes }) {
            "resize" -> obj.int("width").inRange(320..1920) && obj.int("height").inRange(240..1080)
            "pointer" -> validPointer(obj)
            "wheel" -> obj.double("deltaX").inDeltaRange() && obj.double("deltaY").inDeltaRange()
            "key" -> validKey(obj)
            "text" -> (obj.string("value")?.length ?: Int.MAX_VALUE) <= 2048
            "cancel" -> true
            else -> false
        }

    private fun isValidTokenMessage(obj: JsonObject): Boolean =
        when (obj.string("type")?.takeIf { it in tokenTypes }) {
            "status" -> obj.string("phase")?.let { it in phases } == true
            "error" -> (obj.string("message")?.length ?: Int.MAX_VALUE) <= 200
            "log" -> obj.int("at").inRange(0..Int.MAX_VALUE) && (obj.string("message")?.length ?: Int.MAX_VALUE) <= 1000
            else -> false
        }

    private fun validPointer(obj: JsonObject): Boolean =
        obj.string("event")?.let { it in pointerEvents } == true &&
            obj.double("x").inCoordinateRange() &&
            obj.double("y").inCoordinateRange() &&
            obj.string("button")?.let { it in buttons } == true

    private fun validKey(obj: JsonObject): Boolean =
        obj.string("event")?.let { it in keyEvents } == true &&
            (obj.string("key")?.length ?: Int.MAX_VALUE) <= 64 &&
            (obj.string("code")?.length ?: 0) <= 64 &&
            ((obj["modifiers"] as? JsonArray)?.size ?: 0) <= 8

    private fun Double?.inDeltaRange(): Boolean = this != null && this in -5000.0..5000.0

    private fun Double?.inCoordinateRange(): Boolean = this != null && this in 0.0..4096.0

    private fun Int?.inRange(range: IntRange): Boolean = this != null && this in range

    private fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull
}
