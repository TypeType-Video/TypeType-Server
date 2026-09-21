package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.OidcCallbackRequest
import dev.typetype.server.models.OidcPublicConfig
import dev.typetype.server.models.OidcStartResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class OidcAuthService(
    private val config: OidcConfig?,
    jwtSecret: String,
    authService: AuthService,
    httpClient: OkHttpClient = OkHttpClient(),
) {
    private val oidcHttpClient = OidcHttpClient(httpClient)
    private val stateCodec = OidcStateCodec(jwtSecret)
    private val tokenValidator = OidcIdTokenValidator(oidcHttpClient)
    private val userService = OidcUserService(authService)

    fun publicConfig(): OidcPublicConfig = OidcPublicConfig(
        enabled = config != null,
        providerName = config?.providerName,
    )

    suspend fun start(redirectUri: String?, returnTo: String?): ExtractionResult<OidcStartResponse> =
        withContext(Dispatchers.IO) {
            val cfg = config ?: return@withContext ExtractionResult.BadRequest("OIDC is disabled")
            val cleanRedirectUri = redirectUri?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@withContext ExtractionResult.BadRequest("Missing 'redirectUri' parameter")
            runCatching {
                val nonce = UUID.randomUUID().toString()
                val state = stateCodec.create(nonce = nonce, redirectUri = cleanRedirectUri, returnTo = returnTo.cleanReturnTo())
                OidcStartResponse(authorizationUrl = authorizationUrl(cfg, oidcHttpClient.metadata(cfg), cleanRedirectUri, state, nonce))
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "OIDC authorization failed") },
            )
        }

    suspend fun callback(request: OidcCallbackRequest): ExtractionResult<OidcCallbackSession> =
        withContext(Dispatchers.IO) {
            val cfg = config ?: return@withContext ExtractionResult.BadRequest("OIDC is disabled")
            runCatching {
                val state = stateCodec.verify(request.state)
                require(state.redirectUri == request.redirectUri) { "OIDC redirect URI mismatch" }
                val metadata = oidcHttpClient.metadata(cfg)
                val tokenJson = oidcHttpClient.exchangeCode(cfg, metadata, request.code, request.redirectUri)
                val idToken = tokenJson.requiredString("id_token")
                val session = userService.login(tokenValidator.validate(cfg, metadata, idToken, state.nonce))
                OidcCallbackSession(accessToken = session.accessToken, refreshToken = session.refreshToken, returnTo = state.returnTo)
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.BadRequest(it.message ?: "OIDC callback failed") },
            )
        }

    private fun authorizationUrl(config: OidcConfig, metadata: OidcProviderMetadata, redirectUri: String, state: String, nonce: String): String {
        val params = mapOf(
            "client_id" to config.clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to config.scopes,
            "state" to state,
            "nonce" to nonce,
        )
        return metadata.authorizationEndpoint + separator(metadata.authorizationEndpoint) + params.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun String?.cleanReturnTo(): String = this?.takeIf { it.startsWith('/') } ?: "/"

    private fun separator(url: String): String = if ('?' in url) "&" else "?"

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

    private fun JSONObject.requiredString(name: String): String = getString(name).takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("OIDC token response is missing $name")
}
