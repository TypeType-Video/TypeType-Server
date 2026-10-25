package dev.typetype.server.services

class SabrPlaybackInfoResolver(
    private val sessionStore: SabrSessionStore,
    private val authenticatedInfoService: AuthenticatedSabrInfoService?,
    private val publicRequiresYoutubeSession: suspend (String) -> Boolean = { false },
) {
    suspend fun initial(
        userId: String?,
        videoId: String,
        startTimeMs: Long,
    ): SabrPreparedInfo? {
        if (!publicRequiresYoutubeSession(videoId)) {
            return sessionStore.fetchInfo(videoId, startTimeMs, cachedFirst = true)
        }
        return (authenticatedInfoService?.fetch(userId, videoId) as? AuthenticatedSabrInfoResult.Ready)?.prepared
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
