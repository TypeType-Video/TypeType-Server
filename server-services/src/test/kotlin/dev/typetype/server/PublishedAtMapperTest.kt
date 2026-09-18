package dev.typetype.server

import dev.typetype.server.services.PublishedAtMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PublishedAtMapperTest {
    @Test
    fun `fromUploaded returns value when positive`() {
        assertEquals(1_700_000_000_123L, PublishedAtMapper.fromUploaded(1_700_000_000_123L))
    }

    @Test
    fun `fromUploaded returns null when non positive`() {
        assertNull(PublishedAtMapper.fromUploaded(-1L))
        assertNull(PublishedAtMapper.fromUploaded(0L))
    }
}
