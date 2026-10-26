package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** The read policy of docs/02 §5: cached first, a refresh when stale, fresh or stale afterwards. */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheTest {

    private val clock = KyivClock.fixed(Instant.parse("2026-10-26T10:00:00Z"))
    private val cache = Cache(clock)
    private val ttl = Duration.ofMinutes(15)

    @Test
    fun `nothing cached ends fresh once the fetch stored something`() = runTest(UnconfinedTestDispatcher()) {
        val local = MutableStateFlow<String?>(null)

        val state = cache.observe("k", ttl, local, fetch = {
            local.value = "server"
            ApiResult.Ok(Unit)
        }).first { it is CacheState.Fresh }

        assertEquals(CacheState.Fresh("server"), state)
    }

    @Test
    fun `nothing cached and a failed fetch is missing with the error`() = runTest(UnconfinedTestDispatcher()) {
        val local = MutableStateFlow<String?>(null)

        val state = cache.observe("k1", ttl, local, fetch = { ApiResult.Err(ApiError.Network(IOException("offline"))) })
            .first { it.errorOrNull != null }

        assertInstanceOf(CacheState.Missing::class.java, state)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun `cached data with a failed refresh is stale with the error`() = runTest(UnconfinedTestDispatcher()) {
        val local = MutableStateFlow<String?>("old")

        val state = cache.observe("k2", ttl, local, fetch = { ApiResult.Err(ApiError.Network(IOException("offline"))) })
            .first { it.errorOrNull != null }

        assertInstanceOf(CacheState.Stale::class.java, state)
        assertEquals("old", state.dataOrNull)
    }

    @Test
    fun `invalidation refreshes an open observer`() = runTest(UnconfinedTestDispatcher()) {
        val local = MutableStateFlow<String?>("v1")
        var fetches = 0
        val flow = cache.observe("k3", ttl, local, fetch = {
            fetches++
            local.value = "v$fetches"
            ApiResult.Ok(Unit)
        })

        val states = mutableListOf<CacheState<String>>()
        val job = backgroundScope.launch { flow.toList(states) }
        cache.invalidate("k3")
        job.cancel()

        assertTrue(fetches >= 2, "fetched $fetches times")
        assertEquals(CacheState.Fresh("v2"), states.last())
    }

    @Test
    fun `a stale key is refreshed only when its ttl has passed`() = runTest(UnconfinedTestDispatcher()) {
        val local = MutableStateFlow<String?>("v")
        var fetches = 0
        val flow = cache.observe("k4", ttl, local, fetch = {
            fetches++
            ApiResult.Ok(Unit)
        })

        flow.take(1).toList()
        flow.take(1).toList()

        assertEquals(1, fetches)
    }
}
