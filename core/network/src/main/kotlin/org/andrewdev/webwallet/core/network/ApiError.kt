package org.andrewdev.webwallet.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** The machine-readable codes of the backend's `ErrorCode`, plus UNKNOWN for anything newer than this app. */
enum class ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_CREDENTIALS,
    EMAIL_NOT_VERIFIED,
    ACCOUNT_DISABLED,
    ACCOUNT_DELETION_SCHEDULED,
    TOO_MANY_ATTEMPTS,
    TOO_MANY_REQUESTS,
    INVALID_TOKEN,
    INVALID_REFRESH_TOKEN,
    INVALID_CURRENT_PASSWORD,
    INVALID_TWO_FACTOR_CODE,
    TWO_FACTOR_EXPIRED,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    RESOURCE_IN_USE,
    CONFLICT,
    INVALID_EXTERNAL_TOKEN,
    EXTERNAL_SERVICE_UNAVAILABLE,
    INVALID_IMPORT_FILE,
    INTERNAL_ERROR,
    UNKNOWN,
    ;

    companion object {
        fun of(name: String?): ErrorCode = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** Why a call failed (docs/02 §4.3). */
sealed interface ApiError {

    /** The backend answered with an RFC 9457 problem; `detail` is already in the UI language. */
    data class Problem(
        val status: Int,
        val code: ErrorCode,
        val title: String,
        val detail: String?,
        val fieldErrors: Map<String, String>,
        val extensions: JsonObject,
    ) : ApiError {
        /** `restoreToken` of ACCOUNT_DELETION_SCHEDULED. */
        val restoreToken: String? get() = extensions["restoreToken"]?.jsonPrimitive?.content

        /** `deletionScheduledAt` of ACCOUNT_DELETION_SCHEDULED, an ISO instant. */
        val deletionScheduledAt: String? get() = extensions["deletionScheduledAt"]?.jsonPrimitive?.content

        /** Seconds to wait, from `Retry-After` when the backend sent one. */
        val retryAfterSeconds: Int? get() = extensions["retryAfter"]?.jsonPrimitive?.content?.toIntOrNull()
    }

    /** Offline, timeout, DNS: the request never got an answer. */
    data class Network(val cause: IOException) : ApiError

    /** An answer this app does not understand: a non-problem body, a 5xx without a problem, a parsing failure. */
    data class Unexpected(val status: Int?, val cause: Throwable?) : ApiError

    companion object {
        private const val RETRY_AFTER_KEY = "retryAfter"

        /** Parses a problem body; falls back to [Unexpected] when the body is not one. */
        fun parse(status: Int, body: String?, retryAfter: String? = null): ApiError {
            if (body.isNullOrBlank()) return Unexpected(status, null)
            return runCatching {
                val json = Json.parseToJsonElement(body).jsonObject
                val code = ErrorCode.of(json["code"]?.jsonPrimitive?.content)
                val errors = json["errors"]?.jsonArray.orEmpty().mapNotNull { item ->
                    val entry = item.jsonObject
                    val field = entry["field"]?.jsonPrimitive?.content
                    val message = entry["message"]?.jsonPrimitive?.content
                    if (field != null && message != null) field to message else null
                }
                val extensions = buildMap {
                    putAll(json.filterKeys { it !in STANDARD_KEYS })
                    retryAfter?.let { put(RETRY_AFTER_KEY, kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                Problem(
                    status = status,
                    code = code,
                    title = json["title"]?.jsonPrimitive?.content ?: "",
                    detail = json["detail"]?.jsonPrimitive?.content,
                    fieldErrors = errors.toMap(),
                    extensions = JsonObject(extensions),
                )
            }.getOrElse { Unexpected(status, it) }
        }

        private val STANDARD_KEYS = setOf("type", "title", "status", "detail", "instance", "code", "errors")
    }
}

/** The outcome of a call: the value or why there is none. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val error: ApiError) : ApiResult<Nothing>

    val valueOrNull: T? get() = (this as? Ok)?.value

    val errorOrNull: ApiError? get() = (this as? Err)?.error

    val isOk: Boolean get() = this is Ok
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(value))
    is ApiResult.Err -> this
}

inline fun <T> ApiResult<T>.onOk(block: (T) -> Unit): ApiResult<T> = also { if (it is ApiResult.Ok) block(it.value) }

inline fun <T> ApiResult<T>.onErr(block: (ApiError) -> Unit): ApiResult<T> =
    also { if (it is ApiResult.Err) block(it.error) }

inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Ok -> transform(value)
    is ApiResult.Err -> this
}
