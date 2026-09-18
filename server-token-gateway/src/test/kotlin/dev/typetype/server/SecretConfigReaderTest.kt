package dev.typetype.server

import dev.typetype.server.services.SecretConfigReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files

class SecretConfigReaderTest {
    @Test
    fun `read prefers direct environment value`() {
        val env = mapOf(
            "TYPETYPE_TEST_SECRET" to " direct ",
            "TYPETYPE_TEST_SECRET_FILE" to "/missing",
        )
        assertEquals("direct", SecretConfigReader.read("TYPETYPE_TEST_SECRET", env::get))
    }

    @Test
    fun `read loads file environment value`() {
        val file = Files.createTempFile("typetype-secret-", ".txt")
        Files.writeString(file, " file-secret \n")

        val env = mapOf("TYPETYPE_TEST_SECRET_FILE" to file.toString())
        assertEquals("file-secret", SecretConfigReader.read("TYPETYPE_TEST_SECRET", env::get))

        Files.deleteIfExists(file)
    }

    @Test
    fun `read ignores placeholder environment value`() {
        val file = Files.createTempFile("typetype-secret-", ".txt")
        Files.writeString(file, "generated-secret\n")
        val env = mapOf(
            "TYPETYPE_TEST_SECRET" to "SET_ME_YOUTUBE_REMOTE_LOGIN_INTERNAL_TOKEN",
            "TYPETYPE_TEST_SECRET_FILE" to file.toString(),
        )

        assertEquals("generated-secret", SecretConfigReader.read("TYPETYPE_TEST_SECRET", env::get))

        Files.deleteIfExists(file)
    }

    @Test
    fun `read ignores missing secret file`() {
        val env = mapOf("TYPETYPE_TEST_SECRET_FILE" to "/missing/secret")
        assertNull(SecretConfigReader.read("TYPETYPE_TEST_SECRET", env::get))
    }
}
