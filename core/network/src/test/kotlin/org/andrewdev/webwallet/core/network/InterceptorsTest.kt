package org.andrewdev.webwallet.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InterceptorsTest {

    private val server = MockWebServer()
    private val tokens = FakeTokens()
    private val info = ClientInfo(versionName = "1.2.3", versionCode = 1020300, androidSdk = 35, language = { "uk" })
    private val client = OkHttpClient.Builder()
        .addInterceptor(ClientHeadersInterceptor(info))
        .addInterceptor(AuthInterceptor(tokens))
        .addInterceptor(IdempotencyInterceptor())
        .authenticator(TokenAuthenticator(tokens))
        .build()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `every request carries the client headers and the bearer token`() {
        server.enqueue(MockResponse().setBody("[]"))
        tokens.token = "a-1"

        client.newCall(get("/api/v1/accounts")).execute().close()

        val request = server.takeRequest()
        assertEquals("android", request.getHeader("X-WebWallet-Client"))
        assertEquals("1020300", request.getHeader("X-App-Version"))
        assertEquals("WebWallet-Android/1.2.3 (1020300; Android 35)", request.getHeader("User-Agent"))
        assertEquals("uk", request.getHeader("Accept-Language"))
        assertEquals("Bearer a-1", request.getHeader("Authorization"))
    }

    @Test
    fun `auth endpoints get no bearer and no refresh on 401`() {
        server.enqueue(MockResponse().setResponseCode(401))
        tokens.token = "a-1"

        client.newCall(post("/api/v1/auth/login")).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(0, tokens.refreshes)
    }

    @Test
    fun `a 401 refreshes once and replays with the new token`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("[]"))
        tokens.token = "old"
        tokens.next = "new"

        val response = client.newCall(get("/api/v1/accounts")).execute()

        assertEquals(200, response.code)
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        assertEquals(listOf("old"), tokens.rejected)
        response.close()
    }

    @Test
    fun `a second 401 after the refresh is not retried again`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        tokens.token = "old"
        tokens.next = "new"

        val response = client.newCall(get("/api/v1/accounts")).execute()

        assertEquals(401, response.code)
        assertEquals(1, tokens.refreshes)
        assertEquals(2, server.requestCount)
        response.close()
    }

    @Test
    fun `a failed refresh leaves the 401 standing`() {
        server.enqueue(MockResponse().setResponseCode(401))
        tokens.token = "old"
        tokens.next = null

        val response = client.newCall(get("/api/v1/accounts")).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
        response.close()
    }

    @Test
    fun `a creating post carries an idempotency key that survives one connection failure`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val response = client.newCall(post("/api/v1/transactions")).execute()

        assertEquals(201, response.code)
        val first = server.takeRequest().getHeader("Idempotency-Key")
        val second = server.takeRequest().getHeader("Idempotency-Key")
        assertNotNull(first)
        assertEquals(first, second)
        response.close()
    }

    @Test
    fun `other requests carry no idempotency key`() {
        server.enqueue(MockResponse().setBody("{}"))

        client.newCall(post("/api/v1/transactions/transfer-pairs/merge")).execute().close()

        assertNull(server.takeRequest().getHeader("Idempotency-Key"))
    }

    private fun get(path: String): Request = Request.Builder().url(server.url(path)).build()

    private fun post(path: String): Request =
        Request.Builder().url(server.url(path)).post("{}".toRequestBody()).build()

    private class FakeTokens : AccessTokenProvider {
        var token: String? = null
        var next: String? = null
        var refreshes = 0
        val rejected = mutableListOf<String?>()

        override fun accessToken(): String? = token

        override suspend fun refreshAfterRejection(rejected: String?): String? {
            refreshes++
            this.rejected += rejected
            token = next
            return next
        }
    }
}
