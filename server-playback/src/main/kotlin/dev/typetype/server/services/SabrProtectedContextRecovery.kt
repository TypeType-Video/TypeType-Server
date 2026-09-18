package dev.typetype.server.services

class SabrProtectedContextRecovery(
    private val tokenClient: TypetypeTokenSabrTokenClient,
) {
    fun refreshIfRejected(videoId: String, rejectedVisitorData: String?) {
        val currentToken = tokenClient.fetch(videoId)
        if (
            currentToken == null ||
            rejectedVisitorData.isNullOrBlank() ||
            currentToken.visitorData == rejectedVisitorData
        ) {
            tokenClient.fetch(videoId, forceRefresh = true)
        }
    }
}
