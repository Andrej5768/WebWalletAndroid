package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.database.JsonCacheDao
import org.andrewdev.webwallet.core.database.JsonCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Typed access to the `json_cache` table for responses that are cached whole. */
@Singleton
class JsonCache @Inject constructor(private val dao: JsonCacheDao, private val clock: KyivClock) {

    fun <T> observe(key: String, serializer: KSerializer<T>): Flow<T?> = dao.observe(key).map { entry ->
        entry?.let { runCatching { json.decodeFromString(serializer, it.body) }.getOrNull() }
    }

    suspend fun <T> get(key: String, serializer: KSerializer<T>): T? =
        dao.get(key)?.let { runCatching { json.decodeFromString(serializer, it.body) }.getOrNull() }

    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        dao.put(JsonCacheEntity(key, json.encodeToString(serializer, value), clock.now().toEpochMilli()))
    }

    suspend fun delete(key: String) = dao.delete(key)
}
