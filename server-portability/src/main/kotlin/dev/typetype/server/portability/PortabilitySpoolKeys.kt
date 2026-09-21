package dev.typetype.server.portability

import java.security.MessageDigest
import java.util.HexFormat

fun portabilityStableHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .let(HexFormat.of()::formatHex)

fun portabilityCategoryByWireName(value: String): PortabilityCategory =
    PortabilityCategory.entries.firstOrNull { it.wireName == value }
        ?: error("Unknown portability category: $value")
