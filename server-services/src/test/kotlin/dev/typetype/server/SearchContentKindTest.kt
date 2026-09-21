package dev.typetype.server

import dev.typetype.server.services.SearchContentKind
import dev.typetype.server.services.toSearchContentKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchContentKindTest {
    @Test
    fun `raw content filter values map to response content kinds`() {
        assertEquals(SearchContentKind.Channels, "|2|channels".toSearchContentKind(emptyList()))
        assertEquals(SearchContentKind.Playlists, "|3|playlists".toSearchContentKind(emptyList()))
        assertEquals(SearchContentKind.Videos, "|1|videos".toSearchContentKind(emptyList()))
        assertEquals(SearchContentKind.Channels, "YouTube Music|9|music_artists".toSearchContentKind(emptyList()))
        assertEquals(SearchContentKind.Playlists, "YouTube Music|8|music_playlists".toSearchContentKind(emptyList()))
    }
}
