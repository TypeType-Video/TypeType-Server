package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutImportJobStatus(
    val jobId: String,
    val status: String,
    val phase: String,
    val progress: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
    val error: String? = null,
)
