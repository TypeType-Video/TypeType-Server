package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.PublicProfileItem
import dev.typetype.server.models.UserProfileItem
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ProfileService {

    suspend fun getProfile(userId: String): UserProfileItem? = DatabaseFactory.query {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.let {
            UserProfileItem(
                id = it[UsersTable.id],
                role = it[UsersTable.role],
                publicUsername = it[UsersTable.publicUsername],
                bio = it[UsersTable.bio],
                avatarUrl = it[UsersTable.avatarUrl],
                avatarType = it[UsersTable.avatarType],
                avatarCode = it[UsersTable.avatarCode],
            )
        }
    }

    suspend fun setEmojiAvatar(userId: String, code: String, avatarService: AvatarService): Boolean {
        val normalized = avatarService.normalizeEmojiCode(code) ?: return false
        val now = System.currentTimeMillis()
        return DatabaseFactory.query {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[avatarType] = "emoji"
                it[avatarCode] = normalized
                it[avatarUrl] = avatarService.openMojiPath(normalized)
                it[updatedAt] = now
            } > 0
        }
    }

    suspend fun clearAvatar(userId: String): Boolean = DatabaseFactory.query {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[avatarType] = null
            it[avatarCode] = null
            it[avatarUrl] = null
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    suspend fun updateProfile(userId: String, publicUsername: String?, bio: String?): ProfileUpdateResult {
        if (publicUsername != null && publicUsername.length !in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH) return ProfileUpdateResult.UsernameInvalidLength
        if (publicUsername != null && !USERNAME_REGEX.matches(publicUsername)) return ProfileUpdateResult.UsernameInvalidFormat
        if (bio != null && bio.length > MAX_BIO_LENGTH) return ProfileUpdateResult.BioTooLong
        if (publicUsername != null) {
            val taken = DatabaseFactory.query {
                UsersTable.selectAll().where { (UsersTable.publicUsername eq publicUsername) and (UsersTable.id neq userId) }.empty().not()
            }
            if (taken) return ProfileUpdateResult.UsernameTaken
        }
        val updated = DatabaseFactory.query {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.publicUsername] = publicUsername
                it[UsersTable.bio] = bio
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        }
        return if (updated) ProfileUpdateResult.Updated else ProfileUpdateResult.UserNotFound
    }

    suspend fun getPublicProfile(publicUsername: String): PublicProfileItem? = DatabaseFactory.query {
        UsersTable.selectAll().where { UsersTable.publicUsername eq publicUsername }.singleOrNull()?.let {
            PublicProfileItem(
                publicUsername = it[UsersTable.publicUsername] ?: publicUsername,
                bio = it[UsersTable.bio],
                avatarUrl = it[UsersTable.avatarUrl],
                avatarType = it[UsersTable.avatarType],
                avatarCode = it[UsersTable.avatarCode],
            )
        }
    }

    companion object {
        private const val MIN_USERNAME_LENGTH = 3
        private const val MAX_USERNAME_LENGTH = 32
        private const val MAX_BIO_LENGTH = 280
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

        fun isValidPublicUsername(value: String): Boolean =
            value.length in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH && USERNAME_REGEX.matches(value)
    }
}
