package dev.typetype.server

import com.sun.net.httpserver.HttpServer
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.OidcAuthService
import dev.typetype.server.services.OidcConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI

class OidcAuthServiceTest {
    @Test
    fun `start returns provider authorization URL`() = runBlocking {
        val (server, baseUrl) = startDiscoveryServer()
        try {
            val service = OidcAuthService(
                config = OidcConfig(
                    issuer = baseUrl,
                    clientId = "typetype-client",
                    clientSecret = "secret",
                    discoveryUrl = "$baseUrl/.well-known/openid-configuration",
                    scopes = "openid email profile",
                    providerName = "Test OIDC",
                ),
                jwtSecret = "test-secret",
                authService = AuthService("test-secret"),
            )
            val result = service.start(redirectUri = "http://localhost/callback", returnTo = "/settings")
            val url = (result as ExtractionResult.Success).data.authorizationUrl
            val query = URI(url).rawQuery

            assertTrue(url.startsWith("$baseUrl/authorize?"))
            assertTrue(query.contains("client_id=typetype-client"))
            assertTrue(query.contains("redirect_uri=http%3A%2F%2Flocalhost%2Fcallback"))
            assertTrue(query.contains("scope=openid+email+profile"))
            assertTrue(query.contains("state="))
            assertTrue(query.contains("nonce="))
        } finally {
            server.stop(0)
        }
    }
}

private fun startDiscoveryServer(): Pair<HttpServer, String> {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl = "http://127.0.0.1:${server.address.port}"
    server.createContext("/.well-known/openid-configuration") { exchange ->
        val body = """
            {"issuer":"$baseUrl","authorization_endpoint":"$baseUrl/authorize","token_endpoint":"$baseUrl/token","jwks_uri":"$baseUrl/jwks"}
        """.trimIndent().toByteArray()
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.start()
    return server to baseUrl
}
