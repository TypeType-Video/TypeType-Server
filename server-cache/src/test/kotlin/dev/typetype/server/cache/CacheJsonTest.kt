package dev.typetype.server.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheJsonTest {
    @Serializable
    data class Payload(val id: String, val ignored: String = "")

    @Test
    fun ignoresUnknownProperties() {
        val encoded = """{"id":"video","unknown":"value"}"""

        val decoded = CacheJson.decodeFromString<Payload>(encoded)

        assertEquals(Payload(id = "video"), decoded)
        assertEquals("""{"id":"video"}""", CacheJson.encodeToString(Payload(id = "video")))
    }
}
