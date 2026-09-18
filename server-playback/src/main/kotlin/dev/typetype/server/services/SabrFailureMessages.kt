package dev.typetype.server.services

const val SABR_RECOVERABLE_FAILURE_PREFIX = "SABR recoverable failure:"

fun sabrRecoverableFailureMessage(message: String?): String {
    val failure = message ?: "Unknown SABR recoverable failure"
    if (failure.contains("SABR spool", ignoreCase = true)) return failure
    return "$SABR_RECOVERABLE_FAILURE_PREFIX $failure"
}
