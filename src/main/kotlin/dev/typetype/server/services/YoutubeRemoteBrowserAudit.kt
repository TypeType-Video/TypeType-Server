package dev.typetype.server.services

import dev.typetype.server.models.YoutubeRemoteBrowserCompleteRequest
import org.slf4j.LoggerFactory

object YoutubeRemoteBrowserAudit {
    private val log = LoggerFactory.getLogger("YoutubeRemoteLogin")
    private val typePattern = Regex("\"type\"\\s*:\\s*\"([a-z_]{1,32})\"")

    fun startResult(userId: String, result: String) {
        log.info("remote-login start user={} result={}", short(userId), result)
    }

    fun bridgeOpened(tokenSessionId: String) {
        log.info("remote-login bridge session={} token socket open", short(tokenSessionId))
    }

    fun bridgeClosed(tokenSessionId: String, code: Int, reason: String) {
        log.info("remote-login bridge session={} token socket closed code={} reason={}", short(tokenSessionId), code, reason)
    }

    fun bridgeFailed(tokenSessionId: String, error: Throwable) {
        log.warn("remote-login bridge session={} token socket failed: {}", short(tokenSessionId), error.message ?: error.javaClass.simpleName)
    }

    fun bridgeEnded(tokenSessionId: String, texts: Int, frames: Int, inputs: Int) {
        log.info("remote-login bridge session={} ended texts={} frames={} inputs={}", short(tokenSessionId), texts, frames, inputs)
    }

    fun droppedTokenMessage(tokenSessionId: String, text: String) {
        val type = typePattern.find(text)?.groupValues?.get(1) ?: "unknown"
        log.warn("remote-login bridge session={} dropped token message type={} bytes={}", short(tokenSessionId), type, text.length)
    }

    fun completion(request: YoutubeRemoteBrowserCompleteRequest, result: YoutubeRemoteBrowserCompleteResult) {
        val lines = request.cookies.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
        val sapisid = request.cookies.contains("SAPISID")
        val loginInfo = request.cookies.contains("LOGIN_INFO")
        log.info(
            "remote-login completion session={} result={} cookies={} sapisid={} loginInfo={} poToken={} authUser={}",
            short(request.sessionId), result::class.simpleName, lines, sapisid, loginInfo, request.poToken.length, request.authUser,
        )
    }

    private fun short(value: String): String = value.take(8)
}
