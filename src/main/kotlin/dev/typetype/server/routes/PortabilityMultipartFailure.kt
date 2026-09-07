package dev.typetype.server.routes

import java.io.IOException

internal fun Throwable.isMultipartSizeLimit(): Boolean = this is IOException && (
    message?.let {
        (it.startsWith("Multipart content length exceeds limit ") && "formFieldLimit" in it) ||
            (it.startsWith("Limit of ") && " bytes exceeded while searching for " in it)
    } == true
)
