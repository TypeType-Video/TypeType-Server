package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PushDevicesTable
import dev.typetype.server.models.PushDeviceRegistrationRequest
import dev.typetype.server.models.PushDeviceRegistrationResponse
import java.security.MessageDigest
import java.util.UUID
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal class PushDeviceRegistry(
    private val endpointValidator: UnifiedPushEndpointValidator = UnifiedPushEndpointValidator(),
    private val maxDevicesPerAccount: Int = DEFAULT_MAX_DEVICES,
) {
    suspend fun register(userId: String, request: PushDeviceRegistrationRequest): DeviceRegistrationResult {
        val deviceId = request.deviceId.trim()
        val platform = request.platform.trim().lowercase()
        val endpoint = request.endpoint.trim()
        if (!DEVICE_ID.matches(deviceId)) return DeviceRegistrationResult.Invalid("device_id")
        if (platform != "android") return DeviceRegistrationResult.UnsupportedPlatform
        val expiresAt = request.expiresAt
        val now = System.currentTimeMillis()
        if (expiresAt != null && (expiresAt <= now || expiresAt > now + MAX_EXPIRY_MS)) {
            return DeviceRegistrationResult.Invalid("expires_at")
        }
        val uri = when (val result = endpointValidator.validate(endpoint)) {
            is EndpointValidationResult.Valid -> result.uri
            is EndpointValidationResult.Invalid -> return DeviceRegistrationResult.Invalid(result.reason)
        }
        val hash = sha256(uri.toString())
        return DatabaseFactory.query {
            removeExpired(userId, now)
            val existingDevice = PushDevicesTable.selectAll().where {
                (PushDevicesTable.userId eq userId) and (PushDevicesTable.deviceId eq deviceId)
            }.singleOrNull()
            val existingEndpoint = PushDevicesTable.selectAll().where { PushDevicesTable.endpointHash eq hash }.singleOrNull()
            if (existingEndpoint != null && existingEndpoint[PushDevicesTable.id] != existingDevice?.get(PushDevicesTable.id)) {
                return@query DeviceRegistrationResult.EndpointConflict
            }
            if (existingDevice == null) {
                val activeCount = PushDevicesTable.selectAll().where { PushDevicesTable.userId eq userId }.count()
                if (activeCount >= maxDevicesPerAccount) return@query DeviceRegistrationResult.LimitReached
                val id = UUID.randomUUID().toString()
                PushDevicesTable.insert {
                    it[PushDevicesTable.id] = id
                    it[PushDevicesTable.userId] = userId
                    it[PushDevicesTable.deviceId] = deviceId
                    it[PushDevicesTable.platform] = platform
                    it[PushDevicesTable.endpoint] = uri.toString()
                    it[PushDevicesTable.endpointHash] = hash
                    it[PushDevicesTable.expiresAt] = expiresAt
                    it[PushDevicesTable.createdAt] = now
                    it[PushDevicesTable.updatedAt] = now
                }
                DeviceRegistrationResult.Success(PushDeviceRegistrationResponse(id, deviceId, platform, expiresAt, now))
            } else {
                val id = existingDevice[PushDevicesTable.id]
                PushDevicesTable.update({ PushDevicesTable.id eq id }) {
                    it[PushDevicesTable.platform] = platform
                    it[PushDevicesTable.endpoint] = uri.toString()
                    it[PushDevicesTable.endpointHash] = hash
                    it[PushDevicesTable.expiresAt] = expiresAt
                    it[PushDevicesTable.updatedAt] = now
                }
                DeviceRegistrationResult.Success(PushDeviceRegistrationResponse(id, deviceId, platform, expiresAt, now))
            }
        }
    }

    suspend fun unregister(userId: String, deviceId: String): Boolean = DatabaseFactory.query {
        PushDevicesTable.deleteWhere {
            (PushDevicesTable.userId eq userId) and (PushDevicesTable.deviceId eq deviceId)
        } > 0
    }

    suspend fun list(userId: String): List<PushDeviceRegistrationResponse> = DatabaseFactory.query {
        val now = System.currentTimeMillis()
        removeExpired(userId, now)
        PushDevicesTable.selectAll().where { PushDevicesTable.userId eq userId }
            .map { row ->
                PushDeviceRegistrationResponse(
                    id = row[PushDevicesTable.id],
                    deviceId = row[PushDevicesTable.deviceId],
                    platform = row[PushDevicesTable.platform],
                    expiresAt = row[PushDevicesTable.expiresAt],
                    updatedAt = row[PushDevicesTable.updatedAt],
                )
            }
    }

    suspend fun removeById(id: String): Boolean = DatabaseFactory.query {
        PushDevicesTable.deleteWhere { PushDevicesTable.id eq id } > 0
    }

    suspend fun activeDevices(userId: String, now: Long = System.currentTimeMillis()): List<PushDevice> = DatabaseFactory.query {
        removeExpired(userId, now)
        PushDevicesTable.selectAll().where { PushDevicesTable.userId eq userId }.map { row ->
            PushDevice(
                id = row[PushDevicesTable.id],
                userId = row[PushDevicesTable.userId],
                endpoint = row[PushDevicesTable.endpoint],
            )
        }
    }

    suspend fun registeredUserIds(): List<String> = DatabaseFactory.query {
        PushDevicesTable.selectAll().map { it[PushDevicesTable.userId] }.distinct()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun removeExpired(userId: String, now: Long) {
        val expiredIds = PushDevicesTable.selectAll().where {
            PushDevicesTable.userId eq userId
        }.mapNotNull { row ->
            row[PushDevicesTable.expiresAt]?.takeIf { it <= now }?.let { row[PushDevicesTable.id] }
        }
        if (expiredIds.isNotEmpty()) {
            PushDevicesTable.deleteWhere { PushDevicesTable.id inList expiredIds }
        }
    }

    companion object {
        const val DEFAULT_MAX_DEVICES = 8
        private const val MAX_EXPIRY_MS = 366L * 24 * 60 * 60 * 1000
        private val DEVICE_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}

internal data class PushDevice(val id: String, val userId: String, val endpoint: String)

internal sealed interface DeviceRegistrationResult {
    data class Success(val response: PushDeviceRegistrationResponse) : DeviceRegistrationResult
    data class Invalid(val reason: String) : DeviceRegistrationResult
    data object UnsupportedPlatform : DeviceRegistrationResult
    data object EndpointConflict : DeviceRegistrationResult
    data object LimitReached : DeviceRegistrationResult
}
