package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.services.CustomAvatarSaveResult
import dev.typetype.server.services.CustomAvatarService
import dev.typetype.server.services.AvatarBinaryCache
import dev.typetype.server.services.StoredAvatar
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CustomAvatarServiceTest {
    private val service = CustomAvatarService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit {
        TestDatabase.truncateAll()
        transaction {
            UsersTable.insert {
                it[id] = TEST_USER_ID
                it[email] = "avatar@test.local"
                it[passwordHash] = "hash"
                it[name] = "Avatar"
                it[role] = "user"
                it[createdAt] = 0
                it[updatedAt] = 0
            }
        }
    }

    @Test
    fun `stores and serves versioned GIF avatar`() = runBlocking {
        val bytes = "GIF89a-avatar".encodeToByteArray()
        val result = service.save(TEST_USER_ID, bytes)
        val saved = assertInstanceOf(CustomAvatarSaveResult.Saved::class.java, result)
        assertEquals("image/gif", saved.item.mediaType)
        val version = saved.item.avatarUrl.substringAfterLast('/')
        assertArrayEquals(bytes, service.get(TEST_USER_ID, version)?.content)
    }

    @Test
    fun `rejects unsupported and oversized avatars`() = runBlocking {
        assertEquals(CustomAvatarSaveResult.Unsupported, service.save(TEST_USER_ID, "not-image".encodeToByteArray()))
        val oversized = ByteArray(CustomAvatarService.MAX_AVATAR_BYTES + 1).also {
            "GIF89a".encodeToByteArray().copyInto(it)
        }
        assertEquals(CustomAvatarSaveResult.TooLarge, service.save(TEST_USER_ID, oversized))
        assertNull(service.get(TEST_USER_ID, "missing"))
    }

    @Test
    fun `avatar cache evicts by total byte budget`() {
        val cache = AvatarBinaryCache(maxEntries = 10, maxBytes = 10)
        cache.put("one:v1", StoredAvatar("image/gif", ByteArray(6), "v1"))
        cache.put("two:v2", StoredAvatar("image/gif", ByteArray(6), "v2"))
        assertNull(cache.get("one:v1"))
        assertEquals("v2", cache.get("two:v2")?.version)
    }
}
