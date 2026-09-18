package dev.typetype.server.services

data class SabrSessionKey(
    val videoId: String,
    val userId: String,
    val audioItag: Int,
    val audioTrackId: String?,
    val videoItag: Int,
    val startTimeMs: Long,
    val purpose: SabrSessionPurpose = SabrSessionPurpose.MANIFEST,
    val audioOnly: Boolean = false,
    val sourceId: String? = null,
)
