package dev.typetype.server.services

class SabrPlaybackInfoResolver(
    private val sessionStore: SabrSessionStore,
    private val authenticatedInfoService: AuthenticatedSabrInfoService?,
) {
    suspend fun initial(
        userId: String?,
        videoId: String,
        startTimeMs: Long,
    ): SabrPreparedInfo? = when (val authenticated = authenticatedInfoService?.fetch(userId, videoId)) {
        is AuthenticatedSabrInfoResult.Ready -> authenticated.prepared
        AuthenticatedSabrInfoResult.Failed, AuthenticatedSabrInfoResult.TimedOut -> null
        AuthenticatedSabrInfoResult.NotConnected, null ->
            sessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = true)
    }

    suspend fun replacement(holder: SabrSessionHolder, startTimeMs: Long): SabrPreparedInfo? {
        if (holder.source == SabrPreparedSource.PUBLIC) {
            return sessionStore.fetchInfo(holder.key.videoId, startTimeMs, cachedFirst = true)
        }
        return when (val authenticated = authenticatedInfoService?.fetch(holder.key.userId, holder.key.videoId)) {
            is AuthenticatedSabrInfoResult.Ready -> authenticated.prepared
            AuthenticatedSabrInfoResult.Failed,
            AuthenticatedSabrInfoResult.TimedOut,
            AuthenticatedSabrInfoResult.NotConnected,
            null,
            -> null
        }
    }
}
