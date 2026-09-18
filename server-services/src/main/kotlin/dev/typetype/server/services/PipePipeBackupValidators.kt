package dev.typetype.server.services

import io.ktor.http.content.PartData

object PipePipeBackupValidators {
    fun isZipFilePart(part: PartData.FileItem): Boolean {
        val ext = part.originalFileName?.substringAfterLast('.', "")?.lowercase()
        val type = part.contentType?.withoutParameters()?.toString()?.lowercase()
        return ext == "zip" || type == "application/zip" || type == "application/octet-stream"
    }
}
