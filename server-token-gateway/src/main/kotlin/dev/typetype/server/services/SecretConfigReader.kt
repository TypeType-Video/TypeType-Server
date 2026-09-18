package dev.typetype.server.services

import java.nio.file.Files
import java.nio.file.Path

object SecretConfigReader {
    fun read(name: String): String? =
        read(name, System::getenv)

    internal fun read(name: String, env: (String) -> String?): String? =
        envText(env(name)) ?: envText(env("${name}_FILE"))?.let(::readFile)

    private fun readFile(path: String): String? =
        runCatching { Files.readString(Path.of(path)).trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    private fun envText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && it !in PLACEHOLDER_VALUES }

    private val PLACEHOLDER_VALUES = setOf(
        "SET_ME_SHARED_SECRET",
        "SET_ME_YOUTUBE_REMOTE_LOGIN_INTERNAL_TOKEN",
        "SET_ME_YOUTUBE_SESSION_ENCRYPTION_KEY",
        "replace-with-shared-internal-token",
        "replace-with-at-least-32-random-characters",
    )
}
