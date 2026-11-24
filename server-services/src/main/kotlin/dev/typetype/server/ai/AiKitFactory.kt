package dev.typetype.server.ai

import dev.typetype.server.cache.CacheService
import dev.typetype.server.services.SecretConfigReader
import okhttp3.OkHttpClient
import java.net.Proxy

object AiKitFactory {
    fun fromEnvironment(
        cache: CacheService?,
        env: (String) -> String? = System::getenv,
        secret: (String) -> String? = SecretConfigReader::read,
        baseClient: OkHttpClient = OkHttpClient(),
    ): AiKit {
        val config = AiKitConfig.fromEnvironment(env)
        val geminiConfig = GeminiConfig.fromEnvironment(env, secret)
        val proxy = AiOutboundProxy.fromUrl(config.outboundProxyUrl)
        val provider = resolveProvider(config, geminiConfig, proxy, baseClient)
        return ManagedAiKit(
            provider = provider,
            cache = cache,
            rpmLimit = config.requestsPerMinute,
            dailyLimit = config.dailyRequestLimit,
        )
    }

    private fun resolveProvider(
        config: AiKitConfig,
        geminiConfig: GeminiConfig,
        proxy: Proxy?,
        baseClient: OkHttpClient,
    ): AiProvider? = when (config.provider) {
        null, GeminiProvider.PROVIDER_ID -> if (geminiConfig.enabled) {
            GeminiProvider(geminiConfig, config.timeoutMs, proxy, baseClient)
        } else {
            null
        }
        else -> null
    }
}
