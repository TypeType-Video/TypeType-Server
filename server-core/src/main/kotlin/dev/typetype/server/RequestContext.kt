package dev.typetype.server

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asContextElement

private val requestIdContext = ThreadLocal<String?>()

const val REQUEST_ID_HEADER = "X-Request-ID"

fun currentRequestId(): String? = requestIdContext.get()

fun requestContextElement(requestId: String?): ThreadContextElement<String?> =
    requestIdContext.asContextElement(requestId)
