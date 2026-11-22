package dev.typetype.server.services

import java.security.SecureRandom

internal object FocusCaptchaGenerator {
    const val CODE_LENGTH = 24
    // Omit lookalike characters (0/O/o, 1/I/l) so exact mixed-case typing stays unambiguous.
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz"
    private val RANDOM = SecureRandom()

    fun generateCode(): String = buildString(CODE_LENGTH) {
        repeat(CODE_LENGTH) {
            append(ALPHABET[RANDOM.nextInt(ALPHABET.length)])
        }
    }
}
