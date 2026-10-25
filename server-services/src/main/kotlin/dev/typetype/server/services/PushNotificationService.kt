package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PushNotificationBaselinesTable
import dev.typetype.server.db.tables.PushNotificationEventsTable
import dev.typetype.server.models.ChannelNotificationPreference
import dev.typetype.server.models.PushDeviceRegistrationRequest
import dev.typetype.server.models.PushDeliveryAcknowledgementRequest
import dev.typetype.server.models.PushNotificationCapability
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

class PushNotificationService(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val preferenceService: ChannelNotificationPreferenceService,
    private val instanceId: String,
    private val enabled: Boolean = true,
    private val deviceRegistry: PushDeviceRegistry = PushDeviceRegistry(),
    private val sender: PushEndpointSender = UnifiedPushSender(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val deliveryStore = PushNotificationDeliveryStore(clock)

    val capability: PushNotificationCapability = PushNotificationCapability(
        enabled = enabled,
        provider = "unifiedpush",
        eventTypes = listOf(EVENT_TYPE_NEW_VIDEO),
        maxDevicesPerAccount = PushDeviceRegistry.DEFAULT_MAX_DEVICES,
    )

    suspend fun registerDevice(userId: String, request: PushDeviceRegistrationRequest) =
        deviceRegistry.register(userId, request)

    suspend fun listDevices(userId: String) = deviceRegistry.list(userId)

    suspend fun unregisterDevice(userId: String, deviceId: String): Boolean =
        deviceRegistry.unregister(userId, deviceId)

    suspend fun acknowledgeDelivery(
        userId: String,
        request: PushDeliveryAcknowledgementRequest,
    ): PushDeliveryAcknowledgementResult {
        if (!EVENT_ID.matches(request.eventId) || !DEVICE_ID.matches(request.deviceId) ||
            request.outcome !in CLIENT_OUTCOMES ||
            request.reasonCode?.let { !REASON_CODE.matches(it) } == true
        ) return PushDeliveryAcknowledgementResult.InvalidRequest
        return deliveryStore.acknowledge(
            eventId = request.eventId,
            userId = userId,
            deviceId = request.deviceId,
            outcome = request.outcome,
            reasonCode = request.reasonCode,
        ).also { result ->
            when (result) {
                PushDeliveryAcknowledgementResult.Recorded,
                PushDeliveryAcknowledgementResult.AlreadyRecorded -> logger.info(
                    "push_notifications event=client_acknowledged trace={} outcome={} reason={}",
                    traceId(request.eventId),
                    request.outcome,
                    request.reasonCode ?: "none",
                )
                PushDeliveryAcknowledgementResult.NotFound -> logger.warn(
                    "push_notifications event=client_ack_rejected trace={} reason=delivery_not_found",
                    traceId(request.eventId),
                )
                PushDeliveryAcknowledgementResult.Conflict -> logger.warn(
                    "push_notifications event=client_ack_rejected trace={} reason=status_conflict",
                    traceId(request.eventId),
                )
                PushDeliveryAcknowledgementResult.InvalidRequest -> Unit
            }
        }
    }

    suspend fun listPreferences(userId: String): List<ChannelNotificationPreference> =
        preferenceService.list(userId)

    suspend fun setPreference(userId: String, channelUrl: String, value: Boolean): PreferenceUpdateResult {
        val result = preferenceService.set(userId, channelUrl, value)
        if (result is PreferenceUpdateResult.Updated && !value) {
            deliveryStore.clearPending(userId, result.preference.channelUrl)
        }
        return result
    }

    suspend fun setPreferences(
        userId: String,
        enabled: Boolean,
        channelUrls: List<String>?,
    ): List<ChannelNotificationPreference> {
        val updated = preferenceService.setAll(userId, enabled, channelUrls)
        if (!enabled) updated.forEach { deliveryStore.clearPending(userId, it.channelUrl) }
        return updated
    }

    suspend fun onSubscriptionRemoved(userId: String, channelUrl: String) {
        val canonical = ChannelUrlCanonicalizer.canonicalize(channelUrl)
        preferenceService.remove(userId, canonical)
        deliveryStore.clearPending(userId, canonical)
    }

    suspend fun pollRegisteredAccounts() {
        if (!enabled) return
        try {
            deviceRegistry.registeredUserIds().forEach { userId ->
                try {
                    pollUser(userId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    logger.warn("push_notifications event=poll_failed user={} error={}", userKey(userId), error.message)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logger.warn("push_notifications event=registry_failed error={}", error.message)
        }
    }

    suspend fun pollUser(userId: String) {
        val subscriptions = subscriptionsService.getAll(userId)
        if (subscriptions.isEmpty()) return
        val devices = deviceRegistry.activeDevices(userId, clock())
        if (devices.isEmpty()) return
        val feed = subscriptionFeedService.getAllWithSources(userId)
        if (!feed.available) return
        val followedSince = subscriptions.associate {
            ChannelUrlCanonicalizer.canonicalize(it.channelUrl) to it.subscribedAt
        }
        val enabledChannels = preferenceService.enabledChannelUrls(userId)
        val candidates = feed.videos.flatMap { video ->
            PushNotificationSupport.candidates(video, feed.sourceChannelUrls, followedSince)
        }
            .distinctBy { it.serviceId to (it.channelId to it.videoId) }
        val work = DatabaseFactory.query {
            val baseline = PushNotificationBaselinesTable.selectAll()
                .where { PushNotificationBaselinesTable.userId eq userId }.singleOrNull()
            if (baseline == null) {
                PushNotificationBaselinesTable.insert {
                    it[PushNotificationBaselinesTable.userId] = userId
                    it[PushNotificationBaselinesTable.initializedAt] = clock()
                }
                candidates.forEach { deliveryStore.markSeen(userId, it, clock()) }
                return@query emptyList()
            }
            candidates.mapNotNull candidateLoop@{ candidate ->
                val isNew = deliveryStore.markSeen(userId, candidate, clock())
                if (candidate.channelId !in enabledChannels) return@candidateLoop null
                val eventId = PushNotificationSupport.eventId(instanceId, candidate)
                PushNotificationEventsTable.insertIgnore {
                    it[PushNotificationEventsTable.eventId] = eventId
                    it[PushNotificationEventsTable.eventType] = EVENT_TYPE_NEW_VIDEO
                    it[PushNotificationEventsTable.instanceId] = this@PushNotificationService.instanceId
                    it[PushNotificationEventsTable.serviceId] = candidate.serviceId
                    it[PushNotificationEventsTable.channelId] = candidate.channelId
                    it[PushNotificationEventsTable.videoId] = candidate.videoId
                    it[PushNotificationEventsTable.videoUrl] = candidate.video.url
                    it[PushNotificationEventsTable.title] = candidate.video.title
                    it[PushNotificationEventsTable.channelName] = candidate.video.uploaderName
                    it[PushNotificationEventsTable.channelAvatarUrl] = candidate.video.uploaderAvatarUrl
                    it[PushNotificationEventsTable.publishedAt] = candidate.publishedAt
                    it[PushNotificationEventsTable.createdAt] = clock()
                }
                val queued = devices.mapNotNull { device ->
                    if (deliveryStore.claim(eventId, userId, device.deviceId, isNew, clock())) {
                        DeliveryWork(eventId, device, PushNotificationSupport.payload(instanceId, eventId, candidate, userId))
                    } else null
                }
                queued
            }.flatten()
        }
        work.forEach { delivery -> deliver(delivery) }
    }

    private suspend fun deliver(work: DeliveryWork) {
        when (val result = sender.send(work.device.endpoint, work.eventId, work.payload, work.device.p256dh, work.device.auth)) {
            PushSendResult.Delivered -> {
                deliveryStore.update(work, PushNotificationDeliveryStatus.AWAITING_ACK, null)
                logger.info("push_notifications event=distributor_accepted trace={}", traceId(work.eventId))
            }
            PushSendResult.InvalidEndpoint -> {
                deviceRegistry.removeById(work.device.id)
                deliveryStore.update(work, PushNotificationDeliveryStatus.INVALID, "endpoint_invalid")
            }
            PushSendResult.InvalidSubscription -> {
                deviceRegistry.removeById(work.device.id)
                deliveryStore.update(work, PushNotificationDeliveryStatus.INVALID, "subscription_keys_invalid")
            }
            is PushSendResult.Retry -> deliveryStore.update(
                work,
                PushNotificationDeliveryStatus.FAILED,
                result.statusCode?.toString() ?: "network",
            )
        }
    }

    private fun userKey(userId: String): String = userId.take(8)

    private fun traceId(eventId: String): String = eventId.take(16)

    private companion object {
        const val EVENT_TYPE_NEW_VIDEO = "subscription_new_video"
        val EVENT_ID = Regex("[a-f0-9]{64}")
        val DEVICE_ID = Regex("[A-Za-z0-9._:-]{1,128}")
        val CLIENT_OUTCOMES = setOf("posted", "suppressed", "failed")
        val REASON_CODE = Regex("[a-z_]{1,64}")
        val logger = org.slf4j.LoggerFactory.getLogger(PushNotificationService::class.java)
    }
}

data class PushCandidate(
    val serviceId: Int,
    val channelId: String,
    val videoId: String,
    val publishedAt: Long,
    val video: VideoItem,
)

data class DeliveryWork(val eventId: String, val device: PushDevice, val payload: String)
