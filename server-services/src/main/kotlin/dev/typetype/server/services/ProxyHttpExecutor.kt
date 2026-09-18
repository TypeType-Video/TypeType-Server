package dev.typetype.server.services

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class ProxyHttpExecutor(
    client: OkHttpClient,
    private val maxRedirects: Int = 5,
) {
    private val dns = ValidatingProxyDns(client.dns)
    private val client = client.newBuilder()
        .dns(dns)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun execute(initialRequest: Request): Response {
        var request = initialRequest
        val initialTarget = requireProxyTarget(request.url.toString())
        repeat(maxRedirects + 1) { redirectCount ->
            val target = requireProxyTarget(request.url.toString())
            if (target.provider != initialTarget.provider) {
                throw ProxyTargetRejectedException("Cross-provider redirect is not allowed")
            }
            trustConfiguredProxy(target.url)
            dns.lookup(target.url.host)
            val response = client.newCall(request).execute()
            val location = response.header("Location")
            if (!response.isRedirect || location == null) return response
            if (redirectCount == maxRedirects) {
                response.close()
                throw IOException("Too many proxy redirects")
            }
            val nextUrl = response.request.url.resolve(location)
            response.close()
            if (nextUrl == null) throw ProxyTargetRejectedException("Invalid proxy redirect")
            request = request.newBuilder().url(nextUrl).build()
        }
        throw IOException("Too many proxy redirects")
    }

    private fun trustConfiguredProxy(target: HttpUrl) {
        val configured = client.proxy?.let(::listOf)
            ?: client.proxySelector.select(target.toUri())
        configured.forEach { proxy ->
            if (proxy.type() == Proxy.Type.DIRECT) return@forEach
            val address = proxy.address() as? InetSocketAddress ?: return@forEach
            dns.trustTransportHost(address.hostString)
        }
    }
}

class ValidatingProxyDns(private val delegate: Dns) : Dns {
    private val trustedTransportHosts = ConcurrentHashMap.newKeySet<String>()

    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = hostname.lowercase().trimEnd('.')
        val trustedTransport = normalized in trustedTransportHosts
        if (!trustedTransport && providerForProxyHost(normalized) == null) {
            throw UnknownHostException("Unsupported proxy host")
        }
        val addresses = try {
            delegate.lookup(hostname)
        } catch (error: UnknownHostException) {
            throw error
        } catch (error: Exception) {
            throw UnknownHostException(error.message ?: "Unable to resolve proxy host")
        }
        if (addresses.isEmpty()) throw UnknownHostException("Unable to resolve proxy host")
        if (trustedTransport) return addresses
        if (addresses.any { !isPublicProxyAddress(it) }) {
            throw UnknownHostException("Blocked non-public proxy address")
        }
        return addresses
    }

    fun trustTransportHost(hostname: String) {
        trustedTransportHosts += hostname.lowercase().trimEnd('.')
    }
}
