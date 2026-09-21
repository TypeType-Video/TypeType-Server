package dev.typetype.server.portability

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

fun JsonObject.string(name: String): String = get(name)?.let { element ->
    runCatching { element.jsonPrimitive.contentOrNull.orEmpty() }.getOrDefault("")
}.orEmpty()

fun JsonObject.long(name: String): Long = get(name)?.let { element ->
    runCatching { element.jsonPrimitive.contentOrNull?.toLongOrNull() ?: 0L }.getOrDefault(0L)
} ?: 0L

fun JsonObject.int(name: String): Int = long(name).toInt()

fun JsonObject.array(name: String): JsonArray = get(name)?.let { element ->
    runCatching { element.jsonArray }.getOrNull()
} ?: JsonArray(emptyList())

fun JsonElement.stringValue(): String = runCatching {
    jsonPrimitive.contentOrNull.orEmpty()
}.getOrDefault("")

fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

fun JsonObject.primitiveOrNull(name: String): JsonPrimitive? = get(name) as? JsonPrimitive
