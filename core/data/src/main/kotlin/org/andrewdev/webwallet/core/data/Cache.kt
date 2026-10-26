package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What a screen gets from a repository read (docs/02 §5.2). */
sealed interface CacheState<out T> {
    /** Data the backend confirmed within the TTL. */
    data class Fresh<T>(val data: T) : CacheState<T>

    /** Cached data, possibly being refreshed, possibly with the error of the last refresh. */
    data class Stale<T>(val data: T, val fetchedAt: Instant?, val refreshing: Boolean, val error: ApiError?) :
        CacheState<T>

    /** Nothing cached yet: a skeleton while loading, an error screen otherwise. */
    data class Missing(val loading: Boolean, val error: ApiError?) : CacheState<Nothing>

    val dataOrNull: T? get() = when (this) {
        is Fresh -> data
        is Stale -> data
        is Missing -> null
    }

    val errorOrNull: ApiError? get() = when (this) {
        is Fresh -> null
        is Stale -> error
        is Missing -> error
    }

    val isRefreshing: Boolean get() = when (this) {
        is Fresh -> false
        is Stale -> refreshing
        is Missing -> loading
    }
}

inline fun <T, R> CacheState<T>.map(transform: (T) -> R): CacheState<R> = when (this) {
    is CacheState.Fresh -> CacheState.Fresh(transform(data))
    is CacheState.Stale -> CacheState.Stale(transform(data), fetchedAt, refreshing, error)
    is CacheState.Missing -> this
}

/**
 * The read policy of every repository: emit what Room has at once, refresh when the data is older than its TTL or
 * was invalidated, and tell the screen whether what it sees is fresh, stale or missing. Fetch times live in memory,
 * so after a process restart everything counts as stale and is refreshed on first open, as docs/02 §5.1 asks.
 */
@Singleton
class Cache @Inject constructor(private val clock: KyivClock) {

    private val fetchedAt = ConcurrentHashMap<String, Instant>()
    private val invalidations = MutableSharedFlow<String>(extraBufferCapacity = BUFFER)
    private val inFlight = ConcurrentHashMap<String, MutableStateFlow<Status>>()

    /** Forgets the fetch time of the keys and makes every open observer refresh. */
    fun invalidate(vararg keys: String) {
        keys.forEach { key ->
            fetchedAt.remove(key)
            invalidations.tryEmit(key)
        }
    }

    /** Invalidates every key with the prefix, e.g. all feed pages. */
    fun invalidatePrefix(prefix: String) {
        fetchedAt.keys.filter { it.startsWith(prefix) }.forEach { invalidate(it) }
        invalidations.tryEmit(prefix)
    }

    fun forgetAll() {
        fetchedAt.clear()
    }

    /**
     * @param local the cached value, null while nothing was stored
     * @param fetch reads the backend and stores the result; its outcome decides fresh or stale
     */
    fun <T : Any> observe(
        key: String,
        ttl: Duration,
        local: Flow<T?>,
        fetch: suspend () -> ApiResult<*>,
    ): Flow<CacheState<T>> = channelFlow {
        val status = inFlight.getOrPut(key) { MutableStateFlow(Status()) }

        suspend fun refresh() {
            if (status.value.refreshing) return
            status.update { it.copy(refreshing = true, error = null) }
            val result = fetch()
            if (result is ApiResult.Ok) fetchedAt[key] = clock.now()
            status.update { it.copy(refreshing = false, error = result.errorOrNull) }
        }

        launch { if (isStale(key, ttl)) refresh() }
        launch { invalidations.filter { it == key || (it.endsWith(":") && key.startsWith(it)) }.collect { refresh() } }
        combine(local, status) { data, state ->
            when {
                data == null -> CacheState.Missing(loading = state.refreshing, error = state.error)
                state.error == null && !isStale(key, ttl) -> CacheState.Fresh(data)
                else -> CacheState.Stale(data, fetchedAt[key], state.refreshing, state.error)
            }
        }.collect { send(it) }
    }

    private fun isStale(key: String, ttl: Duration): Boolean {
        val at = fetchedAt[key] ?: return true
        return Duration.between(at, clock.now()) > ttl
    }

    private data class Status(val refreshing: Boolean = false, val error: ApiError? = null)

    companion object {
        private const val BUFFER = 64
        val SHORT: Duration = Duration.ofMinutes(15)
        val HOUR: Duration = Duration.ofHours(1)
        val LONG: Duration = Duration.ofHours(6)
    }
}
