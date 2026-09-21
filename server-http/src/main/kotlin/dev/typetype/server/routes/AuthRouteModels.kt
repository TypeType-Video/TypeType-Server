package dev.typetype.server.routes

@kotlinx.serialization.Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@kotlinx.serialization.Serializable
data class RegisterStatusResponse(val allowRegistration: Boolean, val bootstrapAvailable: Boolean, val localLoginEnabled: Boolean = true)

@kotlinx.serialization.Serializable
data class LoginRequest(val identifier: String? = null, val email: String? = null, val password: String)

@kotlinx.serialization.Serializable
data class RefreshRequest(val token: String? = null)

@kotlinx.serialization.Serializable
data class AuthResponse(val token: String)

@kotlinx.serialization.Serializable
data class SessionResponse(val accessToken: String)

@kotlinx.serialization.Serializable
data class ResetPasswordRequest(val resetToken: String, val newPassword: String)
