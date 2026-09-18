package dev.typetype.server.services

class AvatarBinaryCache(
    private val maxEntries: Int = 64,
    private val maxBytes: Long = 64L * 1024 * 1024,
) {
    private val entries = LinkedHashMap<String, StoredAvatar>(maxEntries, 0.75f, true)
    private var storedBytes = 0L

    @Synchronized
    fun get(key: String): StoredAvatar? = entries[key]

    @Synchronized
    fun put(key: String, avatar: StoredAvatar): Unit {
        storedBytes -= entries.put(key, avatar)?.content?.size ?: 0
        storedBytes += avatar.content.size
        while (entries.size > maxEntries || storedBytes > maxBytes) {
            val eldest = entries.entries.firstOrNull() ?: break
            storedBytes -= eldest.value.content.size
            entries.remove(eldest.key)
        }
    }

    @Synchronized
    fun invalidateUser(userId: String): Unit {
        val keys = entries.keys.filter { it.startsWith("$userId:") }
        keys.forEach { key -> storedBytes -= entries.remove(key)?.content?.size ?: 0 }
    }
}

data class StoredAvatar(
    val mediaType: String,
    val content: ByteArray,
    val version: String,
)
