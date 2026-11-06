package org.andrewdev.webwallet.core.network

import okhttp3.MultipartBody
import org.andrewdev.webwallet.core.network.generated.model.BrokerPreviewResponse
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * `POST /imports/freedom/preview` with both reports: the generated client declares a single `files` part, while the
 * backend takes the trades and the cash flows reports as two parts of that name.
 */
interface FreedomUploadApi {
    @Multipart
    @POST("api/v1/imports/freedom/preview")
    suspend fun preview(
        @Part files: List<MultipartBody.Part>,
        @Query("accountId") accountId: Long? = null,
    ): Response<BrokerPreviewResponse>
}
