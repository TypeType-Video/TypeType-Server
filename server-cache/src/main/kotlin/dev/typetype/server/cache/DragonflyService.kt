package dev.typetype.server.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
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

    override suspend fun increment(key: String, ttlSeconds: Long): Long =
        async.eval<Long>(
            INCREMENT_WITH_EXPIRY,
            ScriptOutputType.INTEGER,
            arrayOf(key),
            ttlSeconds.toString(),
        ).await()

    override suspend fun getAndDelete(key: String): String? = async.getdel(key).await()

    override suspend fun delete(key: String): Unit =
        async.del(key).await().let {}

    override suspend fun deleteMatching(pattern: String): Int {
        var deleted = 0
        var cursor: ScanCursor = ScanCursor.INITIAL
        val args = ScanArgs.Builder.matches(pattern).limit(SCAN_BATCH_SIZE)
        do {
            val page = async.scan(cursor, args).await()
            val keys = page.keys
            if (keys.isNotEmpty()) {
                deleted += async.del(*keys.toTypedArray()).await().toInt()
            }
            cursor = page
        } while (!page.isFinished)
        return deleted
    }

    suspend fun ping(): Boolean = async.ping().await() == "PONG"

    fun close() {
        connection.close()
        client.shutdown()
    }

    private companion object {
        const val SCAN_BATCH_SIZE = 200L
        const val REFRESH_IF_VALUE_MATCHES =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) end return 0"
        const val INCREMENT_WITH_EXPIRY =
            "local n = redis.call('incr', KEYS[1]) if n == 1 then redis.call('expire', KEYS[1], ARGV[1]) end return n"
    }
}
