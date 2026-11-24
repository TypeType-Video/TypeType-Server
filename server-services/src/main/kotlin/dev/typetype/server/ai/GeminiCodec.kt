package dev.typetype.server.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object GeminiCodec {
    fun buildRequestBody(request: AiCompletionRequest): String = buildJsonObject {
        val systemText = request.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
        if (systemText != null) {
            put(
                "systemInstruction",
                buildJsonObject {
                    put("parts", buildTextParts(systemText))
                },
            )
        }
        put(
            "contents",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("parts", buildUserParts(request))
                    },
                )
            },
        )
        buildGenerationConfig(request)?.let { put("generationConfig", it) }
    }.toString()

    fun parseSuccessText(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val candidates = root["candidates"] as? JsonArray ?: return null
        val first = candidates.firstOrNull() as? JsonObject ?: return null
        val content = first["content"] as? JsonObject ?: return null
        val parts = content["parts"] as? JsonArray ?: return null
        val text = parts.mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            obj["text"]?.jsonPrimitive?.takeIf { it.isString }?.content
        }.joinToString(separator = "")
        return text.takeIf { it.isNotBlank() }
    }

    fun redactApiKey(text: String, apiKey: String?): String =
        if (!apiKey.isNullOrBlank()) text.replace(apiKey, "[redacted]") else text

    fun extractErrorReason(statusCode: Int, body: String, apiKey: String? = null): String {
        val rawMessage = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.takeIf { it.isString }?.content
        }.getOrNull()
        val redacted = rawMessage?.let { redactApiKey(it, apiKey) }
        val clean = redacted
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(MAX_ERROR_LENGTH)
        return if (clean != null) "http_${statusCode}: $clean" else "http_$statusCode"
    }

    private fun buildTextParts(text: String): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("text", text)
            },
        )
    }

    private fun buildUserParts(request: AiCompletionRequest): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("text", request.userPrompt)
            },
        )
        request.images.forEach { image ->
            add(
                buildJsonObject {
                    put(
                        "inlineData",
                        buildJsonObject {
                            put("mimeType", image.mimeType)
                            put("data", image.base64Data)
                        },
                    )
                },
            )
        }
    }

    private fun buildGenerationConfig(request: AiCompletionRequest): JsonObject? {
        val temperature = request.temperature?.takeIf { it.isFinite() }
        val maxTokens = request.maxOutputTokens?.takeIf { it > 0 }
        val jsonFormat = request.responseFormat as? AiResponseFormat.Json
        if (temperature == null && maxTokens == null && jsonFormat == null) return null
        return buildJsonObject {
            if (temperature != null) put("temperature", temperature)
            if (maxTokens != null) put("maxOutputTokens", maxTokens)
            if (jsonFormat != null) {
                put("responseMimeType", "application/json")
                jsonFormat.schema?.let { put("responseSchema", it) }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private const val MAX_ERROR_LENGTH = 160
}
