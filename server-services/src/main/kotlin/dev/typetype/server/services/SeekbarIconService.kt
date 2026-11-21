package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.UserSeekbarIconsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.SeekbarIconItem
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SeekbarIconService(private val cache: AvatarBinaryCache = AvatarBinaryCache(maxBytes = 16L * 1024 * 1024)) {
    suspend fun save(userId: String, content: ByteArray): SeekbarIconSaveResult {
        if (content.isEmpty()) return SeekbarIconSaveResult.Unsupported
        if (content.size > MAX_ICON_BYTES) return SeekbarIconSaveResult.TooLarge
        val mediaType = UploadedImages.mediaType(content) ?: return SeekbarIconSaveResult.Unsupported
        val version = UploadedImages.version(content)
        val iconUrl = "/seekbar-icon/$userId/$version"
        val saved = DatabaseFactory.query {
            if (UsersTable.selectAll().where { UsersTable.id eq userId }.empty()) return@query false
            val updated = UserSeekbarIconsTable.update({ UserSeekbarIconsTable.userId eq userId }) {
                it[UserSeekbarIconsTable.mediaType] = mediaType
                it[UserSeekbarIconsTable.content] = content
                it[UserSeekbarIconsTable.version] = version
                it[updatedAt] = System.currentTimeMillis()
            }
            if (updated == 0) {
                UserSeekbarIconsTable.insert {
                    it[UserSeekbarIconsTable.userId] = userId
                    it[UserSeekbarIconsTable.mediaType] = mediaType
                    it[UserSeekbarIconsTable.content] = content
                    it[UserSeekbarIconsTable.version] = version
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            writeSetting(userId, icon = "custom", url = iconUrl)
            true
        }
        if (!saved) return SeekbarIconSaveResult.UserNotFound
        cache.invalidateUser(userId)
        cache.put("$userId:$version", StoredAvatar(mediaType, content, version))
        return SeekbarIconSaveResult.Saved(SeekbarIconItem(iconUrl, mediaType, content.size))
    }

    suspend fun get(userId: String, version: String): StoredAvatar? {
        val key = "$userId:$version"
        cache.get(key)?.let { return it }
        val icon = DatabaseFactory.query {
            UserSeekbarIconsTable.selectAll().where {
                (UserSeekbarIconsTable.userId eq userId) and (UserSeekbarIconsTable.version eq version)
            }.singleOrNull()?.let {
                StoredAvatar(it[UserSeekbarIconsTable.mediaType], it[UserSeekbarIconsTable.content], it[UserSeekbarIconsTable.version])
            }
        } ?: return null
        cache.put(key, icon)
        return icon
    }

    suspend fun delete(userId: String) {
        DatabaseFactory.query {
            UserSeekbarIconsTable.deleteWhere { UserSeekbarIconsTable.userId eq userId }
            SettingsTable.update({ (SettingsTable.userId eq userId) and (SettingsTable.seekbarIcon eq "custom") }) {
                it[seekbarIcon] = "typetype"
            }
            SettingsTable.update({ SettingsTable.userId eq userId }) { it[seekbarIconUrl] = "" }
        }
        cache.invalidateUser(userId)
    }

    private fun writeSetting(userId: String, icon: String, url: String) {
        val updated = SettingsTable.update({ SettingsTable.userId eq userId }) {
            it[seekbarIcon] = icon
            it[seekbarIconUrl] = url
        }
        if (updated == 0) {
            SettingsTable.insert {
                it[SettingsTable.userId] = userId
                it[seekbarIcon] = icon
                it[seekbarIconUrl] = url
            }
        }
    }

    companion object {
        const val MAX_ICON_BYTES = 2 * 1024 * 1024
    }
}

sealed interface SeekbarIconSaveResult {
    data class Saved(val item: SeekbarIconItem) : SeekbarIconSaveResult
    data object TooLarge : SeekbarIconSaveResult
    data object Unsupported : SeekbarIconSaveResult
    data object UserNotFound : SeekbarIconSaveResult
}
