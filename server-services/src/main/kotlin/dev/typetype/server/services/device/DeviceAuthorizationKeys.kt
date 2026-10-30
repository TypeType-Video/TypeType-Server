package dev.typetype.server.services.device

import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class DeviceAuthorizationKeys(secret: String) {
    private val key = SecretKeySpec("device-authorization:$secret".toByteArray(), ALGORITHM)

    fun hash(value: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        return HexFormat.of().formatHex(mac.doFinal(value.toByteArray()))
    }

    fun request(deviceHash: String) = "$PREFIX:request:$deviceHash"
    fun decision(deviceHash: String) = "$PREFIX:decision:$deviceHash"
    fun scanned(deviceHash: String) = "$PREFIX:scanned:$deviceHash"
    fun poll(deviceHash: String) = "$PREFIX:poll:$deviceHash"
    fun userCode(userCode: String) = "$PREFIX:user-code:${hash(userCode)}"
    fun starts(client: String) = "$PREFIX:starts:${hash(client)}"
    fun accountFailures(userId: String) = "$PREFIX:failures:account:${hash(userId)}"
    fun clientFailures(client: String) = "$PREFIX:failures:client:${hash(client)}"

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val PREFIX = "device-auth:v1"
    }
}
