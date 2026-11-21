package dev.typetype.server.services

/** What an exit must pass before the egress stays on it. */
interface EgressExitCheck {
    /** Asks the token service for a session minted on the exit now in use. */
    suspend fun renew(videoId: String)

    /** True when the provider serves the video from the middle without asking for attestation. */
    suspend fun accepts(videoId: String): Boolean
}
