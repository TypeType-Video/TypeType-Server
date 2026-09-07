package dev.typetype.server.services

import dev.typetype.server.sabr.SabrRecoverableException

internal class SabrUnauthorizedResponseRecovery(
    private val refreshPoToken: (SabrSessionHolder) -> SabrTokenBundle?,
) {
    fun verify(holder: SabrSessionHolder): Unit {
        val status = latestUnauthorizedStatus(holder.session.diagnosticTrace) ?: return
        if (!holder.markUnauthorizedRefreshAttempted()) throw unauthorized(status)
        val refreshed = refreshPoToken(holder) ?: throw unauthorized(status)
        val token = refreshed.streamingPoTokenBytesFor(holder.info)
            ?.takeUnless { holder.session.streamState.poToken?.contentEquals(it) == true }
            ?: throw unauthorized(status)
        holder.playerContextToken = refreshed
        holder.session.streamState.setPoToken(token)
        holder.session.addDiagnosticEvent("upstream $status refreshed TypeType PO token")
    }

    private fun unauthorized(status: Int): SabrRecoverableException =
        SabrRecoverableException("SABR upstream unauthorized HTTP $status after TypeType token refresh")

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
