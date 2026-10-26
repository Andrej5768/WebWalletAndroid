package org.andrewdev.webwallet.core.network

import retrofit2.Response
import java.io.IOException

/**
 * Runs a generated API call and turns its outcome into an [ApiResult]: a 2xx body becomes [ApiResult.Ok], a problem
 * body becomes [ApiError.Problem], an [IOException] becomes [ApiError.Network] and anything else [ApiError.Unexpected].
 * A 2xx without a body (204) yields [Unit] for calls declared as `Response<Unit>`.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> apiCall(block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        @Suppress("UNCHECKED_CAST")
        ApiResult.Ok(body ?: (Unit as T))
    } else {
        ApiResult.Err(
            ApiError.parse(response.code(), response.errorBody()?.string(), response.headers()["Retry-After"]),
        )
    }
} catch (e: IOException) {
    ApiResult.Err(ApiError.Network(e))
} catch (e: Exception) {
    ApiResult.Err(ApiError.Unexpected(null, e))
}
