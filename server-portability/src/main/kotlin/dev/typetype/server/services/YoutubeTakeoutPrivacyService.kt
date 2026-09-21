package dev.typetype.server.services

import java.nio.file.Files
import java.nio.file.Path

class YoutubeTakeoutPrivacyService {
    fun deleteArchive(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { Files.deleteIfExists(Path.of(path)) }
    }
}
