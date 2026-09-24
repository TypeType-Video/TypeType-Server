package dev.typetype.server

import kotlinx.coroutines.asContextElement
import kotlin.coroutines.CoroutineContext

private val requestIdContext = ThreadLocal<String?>()
private val playbackTraceIdContext = ThreadLocal<String?>()

const val REQUEST_ID_HEADER = "X-Request-ID"
const val PLAYBACK_TRACE_ID_HEADER = "X-Playback-Trace-ID"

fun currentRequestId(): String? = requestIdContext.get()
fun currentPlaybackTraceId(): String? = playbackTraceIdContext.get()

fun requestContextElement(requestId: String?, playbackTraceId: String?): CoroutineContext =
    requestIdContext.asContextElement(requestId) + playbackTraceIdContext.asContextElement(playbackTraceId)
