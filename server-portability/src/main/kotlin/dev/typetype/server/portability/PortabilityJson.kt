package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.core.StreamReadFeature
import dev.typetype.server.cache.CacheJson
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.io.ByteArrayOutputStream

val PortabilityJsonFactory: JsonFactory = JsonFactory.builder()
    .streamReadConstraints(
        StreamReadConstraints.builder()
            .maxNestingDepth(100)
            .maxStringLength(PortabilityLimits.MAX_RECORD_JSON_BYTES)
            .maxNumberLength(1_000)
            .build(),
    )
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
    .build()

inline fun <T> PortabilityInput.withJsonParser(block: (JsonParser) -> T): T =
    Files.newInputStream(path).buffered().use { input ->
        PortabilityJsonFactory.createParser(input).use(block)
    }

fun JsonParser.requireObject() {
    require(nextToken() == JsonToken.START_OBJECT) { "Backup root must be a JSON object" }
}

fun JsonParser.textOrEmpty(): String =
    if (currentToken().isScalarValue) valueAsString.orEmpty() else ""

fun JsonParser.longOrZero(): Long =
    if (currentToken().isNumeric) longValue else valueAsString?.toLongOrNull() ?: 0L

fun JsonParser.readJsonElement(): JsonElement {
    val bytes = ByteArrayOutputStream()
    PortabilityJsonFactory.createGenerator(bytes).use { generator ->
        generator.copyCurrentStructure(this)
    }
    require(bytes.size() <= PortabilityLimits.MAX_RECORD_JSON_BYTES) { "JSON value is too large" }
    return CacheJson.parseToJsonElement(bytes.toString(Charsets.UTF_8))
}

fun youtubeChannelUrl(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.isNotBlank() -> "https://www.youtube.com/channel/$trimmed"
        else -> ""
    }
}

fun youtubeVideoUrl(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.isNotBlank() -> "https://www.youtube.com/watch?v=$trimmed"
        else -> ""
    }
}

fun youtubeId(value: String): String = value.substringAfterLast('/').substringAfterLast('=').takeWhile {
    it != '&' && it != '?' && it != '#'
}
