package org.andrewdev.webwallet.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.UUID

/** What the headers of every request say about this app; read once from BuildConfig and the UI language. */
data class ClientInfo(
    val versionName: String,
    val versionCode: Int,
    val androidSdk: Int,
    /** `uk` or `en`; the backend localises `detail` of its problems with it. */
    val language: () -> String,
)

/** Adds the identification headers of B6 and the UI language (docs/02 §4.2, step 1). */
class ClientHeadersInterceptor(private val info: ClientInfo) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header(
                "User-Agent",
                "WebWallet-Android/${info.versionName} (${info.versionCode}; Android ${info.androidSdk})",
            )
            .header(CLIENT_HEADER, "android")
            .header(VERSION_HEADER, info.versionCode.toString())
            .header("Accept-Language", info.language())
            .build(),
    )

    companion object {
        const val CLIENT_HEADER = "X-WebWallet-Client"
        const val VERSION_HEADER = "X-App-Version"
    }
}

/** The access token in memory and the way to get a new one; implemented by the session manager of core:auth. */
interface AccessTokenProvider {
    fun accessToken(): String?

    /**
     * Refreshes the session because [rejected] was refused with 401; single-flight. Returns the new token, or null
     * when the session is gone (the implementation then signs the user out).
     */
    suspend fun refreshAfterRejection(rejected: String?): String?
}

/** Adds `Authorization: Bearer …` to every request but the public auth endpoints (docs/02 §4.2, step 2). */
class AuthInterceptor(private val tokens: AccessTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isPublic()) return chain.proceed(request)
        val token = tokens.accessToken() ?: return chain.proceed(request)
        return chain.proceed(request.withBearer(token))
    }
}

/**
 * Gives creating POSTs an `Idempotency-Key` and retries them once on a connection failure with the same key, so a
 * request that reached the server but lost its answer is not applied twice (B5; harmless before the backend has it).
 */
class IdempotencyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "POST" || !request.url.encodedPath.isCreatingPath()) return chain.proceed(request)
        val keyed = request.newBuilder().header(HEADER, UUID.randomUUID().toString()).build()
        return try {
            chain.proceed(keyed)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            chain.proceed(keyed)
        }
    }

    private fun String.isCreatingPath(): Boolean = CREATING_PATHS.any { this == it }

    companion object {
        const val HEADER = "Idempotency-Key"
        private val CREATING_PATHS = listOf(
            "/api/v1/transactions",
            "/api/v1/transactions/batch",
            "/api/v1/investments/operations",
            "/api/v1/goals",
            "/api/v1/recurring",
            "/api/v1/categories",
            "/api/v1/tags",
            "/api/v1/payees",
            "/api/v1/categorization-rules",
        )
    }
}

/**
 * On a 401 of an authenticated request refreshes the session once and replays the request with the new token. The
 * refresh is single-flight in the provider; a failed refresh means the user is signed out and the 401 stands.
 */
class TokenAuthenticator(private val tokens: AccessTokenProvider) : okhttp3.Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.isPublic() || response.priorResponse != null) return null
        val rejected = request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = kotlinx.coroutines.runBlocking { tokens.refreshAfterRejection(rejected) } ?: return null
        return request.withBearer(fresh)
    }
}

private fun Request.isPublic(): Boolean {
    val path = url.encodedPath
    return path.startsWith("/api/v1/auth/") || path.startsWith("/.well-known/") || path == "/api/v1/app/config"
}

private fun Request.withBearer(token: String): Request = newBuilder().header("Authorization", "Bearer $token").build()
