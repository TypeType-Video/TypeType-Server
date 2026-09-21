package dev.typetype.server.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

class DragonflyService(url: String) : CacheService {

    private val client: RedisClient = RedisClient.create(url)
    private val connection: StatefulRedisConnection<String, String> =
        client.connect()

    private val async: RedisAsyncCommands<String, String> = connection.async()

    override suspend fun get(key: String): String? = async.get(key).await()

    override suspend fun set(key: String, value: String, ttlSeconds: Long): Unit =
        async.setex(key, ttlSeconds, value).await().let {}

    override suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean =
        async.set(key, value, SetArgs.Builder.nx().ex(ttlSeconds)).await() == "OK"

    override suspend fun refreshIfValueMatches(key: String, value: String, ttlSeconds: Long): Boolean =
        async.eval<Long>(
            REFRESH_IF_VALUE_MATCHES,
            ScriptOutputType.INTEGER,
            arrayOf(key),
            value,
            ttlSeconds.toString(),
        ).await() == 1L

    override suspend fun delete(key: String): Unit =
        async.del(key).await().let {}

    suspend fun ping(): Boolean = async.ping().await() == "PONG"

    fun close() {
        connection.close()
        client.shutdown()
    }

    private companion object {
        const val REFRESH_IF_VALUE_MATCHES =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) end return 0"
    }
}
