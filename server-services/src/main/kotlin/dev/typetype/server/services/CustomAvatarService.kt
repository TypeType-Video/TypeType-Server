package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UserAvatarsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.CustomAvatarItem
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest

class CustomAvatarService(private val cache: AvatarBinaryCache = AvatarBinaryCache()) {
    suspend fun save(userId: String, content: ByteArray): CustomAvatarSaveResult {
        if (content.isEmpty()) return CustomAvatarSaveResult.Unsupported
        if (content.size > MAX_AVATAR_BYTES) return CustomAvatarSaveResult.TooLarge
        val mediaType = detectMediaType(content) ?: return CustomAvatarSaveResult.Unsupported
        val version = content.sha256().take(VERSION_LENGTH)
        val avatarUrl = "/avatar/custom/$userId/$version"
        val updated = DatabaseFactory.query {
            if (UsersTable.selectAll().where { UsersTable.id eq userId }.empty()) return@query false
            val existing = UserAvatarsTable.selectAll().where { UserAvatarsTable.userId eq userId }.empty().not()
            if (existing) {
                UserAvatarsTable.update({ UserAvatarsTable.userId eq userId }) {
                    it[UserAvatarsTable.mediaType] = mediaType
                    it[UserAvatarsTable.content] = content
                    it[UserAvatarsTable.version] = version
                    it[updatedAt] = System.currentTimeMillis()
                }
            } else {
                UserAvatarsTable.insert {
                    it[UserAvatarsTable.userId] = userId
                    it[UserAvatarsTable.mediaType] = mediaType
                    it[UserAvatarsTable.content] = content
                    it[UserAvatarsTable.version] = version
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            UsersTable.update({ UsersTable.id eq userId }) {
                it[avatarType] = "custom"
                it[avatarCode] = null
                it[UsersTable.avatarUrl] = avatarUrl
                it[updatedAt] = System.currentTimeMillis()
            }
            true
        }
        if (!updated) return CustomAvatarSaveResult.UserNotFound
        cache.invalidateUser(userId)
        cache.put("$userId:$version", StoredAvatar(mediaType, content, version))
        return CustomAvatarSaveResult.Saved(CustomAvatarItem(avatarUrl, mediaType, content.size))
    }

    suspend fun get(userId: String, version: String): StoredAvatar? {
        val key = "$userId:$version"
        cache.get(key)?.let { return it }
        val avatar = DatabaseFactory.query {
            UserAvatarsTable.selectAll().where {
                (UserAvatarsTable.userId eq userId) and (UserAvatarsTable.version eq version)
            }.singleOrNull()?.let {
                StoredAvatar(it[UserAvatarsTable.mediaType], it[UserAvatarsTable.content], it[UserAvatarsTable.version])
            }
        } ?: return null
        cache.put(key, avatar)
        return avatar
    }

    suspend fun delete(userId: String): Unit {
        DatabaseFactory.query { UserAvatarsTable.deleteWhere { UserAvatarsTable.userId eq userId } }
        cache.invalidateUser(userId)
    }

    private fun detectMediaType(bytes: ByteArray): String? = when {
        bytes.startsWith(PNG_SIGNATURE) -> "image/png"
        bytes.startsWith(JPEG_SIGNATURE) -> "image/jpeg"
        bytes.startsWith(GIF_87_SIGNATURE) || bytes.startsWith(GIF_89_SIGNATURE) -> "image/gif"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals(WEBP_RIFF) &&
            bytes.copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE) -> "image/webp"
        else -> null
    }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean = size >= signature.size &&
        copyOfRange(0, signature.size).contentEquals(signature)

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_AVATAR_BYTES = 10 * 1024 * 1024
        private const val VERSION_LENGTH = 20
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val GIF_87_SIGNATURE = "GIF87a".encodeToByteArray()
        private val GIF_89_SIGNATURE = "GIF89a".encodeToByteArray()
        private val WEBP_RIFF = "RIFF".encodeToByteArray()
        private val WEBP_SIGNATURE = "WEBP".encodeToByteArray()
    }
}

sealed interface CustomAvatarSaveResult {
    data class Saved(val item: CustomAvatarItem) : CustomAvatarSaveResult
    data object TooLarge : CustomAvatarSaveResult
    data object Unsupported : CustomAvatarSaveResult
    data object UserNotFound : CustomAvatarSaveResult
}
