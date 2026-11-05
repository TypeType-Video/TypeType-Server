package dev.typetype.server.services

/**
 * hls.js asks NicoNico segments one after the other, and each one the server has not fetched yet
 * costs a full trip to the CDN, after a seek too. Knowing which segment follows which, and which
 * audio segment plays beside a video one, lets the server fetch them while the player still
 * downloads the current one. The web player always plays the first audio stream.
 */
class HlsSegmentChain(private val maxSegments: Int = DEFAULT_MAX_SEGMENTS) {
    class Segment(val handle: String, val startMs: Long)

    private class Group(val playlists: List<List<Segment>>, val audio: List<Segment>?) {
        val size = playlists.sumOf { it.size }
    }

    private class Position(val group: Group, val playlist: Int, val index: Int)

    private val groups = LinkedHashMap<String, Group>(16, 0.75f, true)
    private val positions = HashMap<String, Position>()
    private var size = 0

    @Synchronized
    fun remember(video: List<List<Segment>>, audio: List<Segment>?) {
        val playlists = (video + listOfNotNull(audio)).filter { it.isNotEmpty() }
        val key = playlists.firstOrNull()?.first()?.handle ?: return
        if (groups[key] != null) return
        val group = Group(playlists, audio?.takeIf { it.isNotEmpty() })
        if (group.size > maxSegments) return
        groups[key] = group
        size += group.size
        playlists.forEachIndexed { playlist, segments ->
            segments.forEachIndexed { index, segment -> positions[segment.handle] = Position(group, playlist, index) }
        }
        while (size > maxSegments) evictEldest()
    }

    @Synchronized
    fun following(handle: String): List<String> {
        val position = positions[handle] ?: return emptyList()
        val segments = position.group.playlists[position.playlist]
        val next = segments.getOrNull(position.index + 1)?.handle
        val audio = position.group.audio?.takeIf { it !== segments }
            ?.let { at(it, segments[position.index].startMs)?.handle }
        return listOfNotNull(next, audio)
    }

    private fun at(segments: List<Segment>, timeMs: Long): Segment? {
        val index = segments.binarySearchBy(timeMs) { it.startMs }
        return segments.getOrNull(if (index >= 0) index else -index - 2)
    }

    private fun evictEldest() {
        val eldest = groups.entries.iterator()
        if (!eldest.hasNext()) return
        val group = eldest.next().value
        eldest.remove()
        size -= group.size
        group.playlists.forEach { segments ->
            segments.forEach { segment -> if (positions[segment.handle]?.group === group) positions.remove(segment.handle) }
        }
    }

    companion object {
        const val DEFAULT_MAX_SEGMENTS = 60_000
    }
}
