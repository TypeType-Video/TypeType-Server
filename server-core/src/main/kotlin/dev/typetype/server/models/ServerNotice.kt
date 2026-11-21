package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ServerNotice(
    val kind: String,
    val message: String? = null,
    val resumeInSeconds: Int? = null,
    val issuedAt: Long,
)

@Serializable
data class ServerHello(
    val bootId: String,
    val notice: ServerNotice? = null,
)

@Serializable
data class ServerNoticeRequest(
    val kind: String,
    val message: String? = null,
    val resumeInSeconds: Int? = null,
)
