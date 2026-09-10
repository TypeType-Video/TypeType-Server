package dev.typetype.server.services

import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

internal class UnifiedPushEndpointValidator(
    private val resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) {
    fun validate(raw: String): EndpointValidationResult {
        if (raw.length !in 1..2048) return EndpointValidationResult.Invalid("endpoint_length")
        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return EndpointValidationResult.Invalid("endpoint_uri")
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null || uri.fragment != null) {
            return EndpointValidationResult.Invalid("endpoint_scheme")
        }
        val host = uri.host?.trim()?.trim('[', ']')?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return EndpointValidationResult.Invalid("endpoint_host")
        if (uri.port == 0 || uri.port < -1 || uri.port > 65535) {
            return EndpointValidationResult.Invalid("endpoint_port")
        }
        val addresses = try {
            resolver(host).toList()
        } catch (_: UnknownHostException) {
            return EndpointValidationResult.Invalid("endpoint_unresolvable")
        } catch (_: Exception) {
            return EndpointValidationResult.Invalid("endpoint_unresolvable")
        }
        if (addresses.isEmpty() || addresses.any(::isBlockedAddress)) {
            return EndpointValidationResult.Invalid("endpoint_private_address")
        }
        return EndpointValidationResult.Valid(uri, addresses)
    }

    private fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val bytes = address.address
        if (bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()) {
            return isBlockedIpv4(bytes.copyOfRange(12, 16))
        }
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            return first in 0xfc..0xfd
        }
        return bytes.size == 4 && isBlockedIpv4(bytes)
    }

    private fun isBlockedIpv4(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 0 || first == 10 || first == 127 ||
            (first == 100 && second in 64..127) ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 0) ||
            (first == 192 && second == 168) ||
            (first == 198 && second in 18..19) ||
            first >= 224
    }
}

internal sealed interface EndpointValidationResult {
    data class Valid(val uri: URI, val addresses: List<InetAddress>) : EndpointValidationResult
    data class Invalid(val reason: String) : EndpointValidationResult
}
