package dev.typetype.server.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelSerializationTest {
    @Test
    fun serializesProgressItem() {
        val item = ProgressItem(videoUrl = "https://example.test/video", position = 12L)

        val encoded = Json.encodeToString(item)
        val decoded = Json.decodeFromString<ProgressItem>(encoded)

        assertEquals(item, decoded)
    }
}
