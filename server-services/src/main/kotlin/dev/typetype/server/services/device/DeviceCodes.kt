package dev.typetype.server.services.device

import java.security.SecureRandom
import java.util.Base64

object DeviceCodes {
    const val USER_CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ23456789"
    const val USER_CODE_LENGTH = 8
    const val DEVICE_NAME_MAX_LENGTH = 64
    const val DEFAULT_DEVICE_NAME = "TypeType TV"
    private const val DEVICE_CODE_BYTES = 32

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val deviceCodePattern = Regex("^[A-Za-z0-9_-]{43}$")

    fun deviceCode(): String = ByteArray(DEVICE_CODE_BYTES).also(random::nextBytes).let(encoder::encodeToString)

    fun userCode(): String = buildString {
        repeat(USER_CODE_LENGTH) { append(USER_CODE_ALPHABET[random.nextInt(USER_CODE_ALPHABET.length)]) }
    }

    fun display(userCode: String): String = "${userCode.take(4)}-${userCode.drop(4)}"

    fun normalizeUserCode(input: String): String? {
        val compact = input.uppercase().filterNot { it == '-' || it.isWhitespace() }
        return compact.takeIf { it.length == USER_CODE_LENGTH && it.all(USER_CODE_ALPHABET::contains) }
    }

    fun isDeviceCode(input: String): Boolean = deviceCodePattern.matches(input)

    fun deviceName(input: String?): String {
        val cleaned = input.orEmpty()
            .map { if (it.isISOControl()) ' ' else it }
            .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .joinToString(" ")
        return cleaned.take(DEVICE_NAME_MAX_LENGTH).trim().ifEmpty { DEFAULT_DEVICE_NAME }
    }
}
