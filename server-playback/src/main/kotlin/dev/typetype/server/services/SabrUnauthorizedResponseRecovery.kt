package dev.typetype.server.services

class SabrUnauthorizedResponseRecovery(
    private val onRefused: () -> Unit = {},
    private val refreshPoToken: (SabrSessionHolder) -> SabrTokenBundle?,
) {
    fun verify(holder: SabrSessionHolder): Unit {
        val status = latestUnauthorizedStatus(holder.session.diagnosticTrace) ?: return
        if (!holder.markUnauthorizedRefreshAttempted()) throw refused(status)
        val refreshed = refreshPoToken(holder) ?: throw refused(status)
        if (refreshed.changesProviderSession(holder)) throw refused(status, providerSessionChanged = true)
        val token = refreshed.streamingPoTokenBytesFor(holder.info)
            ?.takeUnless { holder.session.streamState.poToken?.contentEquals(it) == true }
            ?: throw refused(status)
        holder.playerContextToken = refreshed
        holder.session.streamState.setPoToken(token)
        holder.session.addDiagnosticEvent("upstream $status refreshed TypeType PO token")
    }

    fun fail(holder: SabrSessionHolder, error: SabrUpstreamUnauthorizedException): Unit {
        if (!error.providerSessionChanged) onRefused()
        holder.failTerminal(sabrRecoverableFailureMessage(error.message), error.kind)
    }

    private fun refused(status: Int, providerSessionChanged: Boolean = false): SabrUpstreamUnauthorizedException =
        SabrUpstreamUnauthorizedException(status, providerSessionChanged)

    private fun SabrTokenBundle.changesProviderSession(holder: SabrSessionHolder): Boolean {
        val current = holder.playerContextToken
        val generationChanged = generation != null && current?.generation != null && generation != current.generation
        val visitorChanged = visitorData != holder.info.visitorData && sessionBinding != holder.info.visitorData
        return generationChanged || visitorChanged
    }

    companion object {
        fun latestUnauthorizedStatus(trace: String): Int? {
            val response = trace.substringAfterLast("response n=", missingDelimiterValue = "")
            return when {
                response.contains(" http=401 ") -> 401
                response.contains(" http=403 ") -> 403
                else -> null
            }
        }
    }
}
