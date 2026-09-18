package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ProfileNameRequest(val name: String)
