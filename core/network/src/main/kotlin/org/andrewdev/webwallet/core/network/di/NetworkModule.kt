package org.andrewdev.webwallet.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.andrewdev.webwallet.core.network.AccessTokenProvider
import org.andrewdev.webwallet.core.network.AuthInterceptor
import org.andrewdev.webwallet.core.network.ClientHeadersInterceptor
import org.andrewdev.webwallet.core.network.ClientInfo
import org.andrewdev.webwallet.core.network.IdempotencyInterceptor
import org.andrewdev.webwallet.core.network.RedactingLogger
import org.andrewdev.webwallet.core.network.TokenAuthenticator
import org.andrewdev.webwallet.core.network.generated.api.AccountsApi
import org.andrewdev.webwallet.core.network.generated.api.AppApi
import org.andrewdev.webwallet.core.network.generated.api.AuthenticationApi
import org.andrewdev.webwallet.core.network.generated.api.BudgetApi
import org.andrewdev.webwallet.core.network.generated.api.CategoriesApi
import org.andrewdev.webwallet.core.network.generated.api.CurrentUserApi
import org.andrewdev.webwallet.core.network.generated.api.DebtsApi
import org.andrewdev.webwallet.core.network.generated.api.DemoApi
import org.andrewdev.webwallet.core.network.generated.api.ForecastApi
import org.andrewdev.webwallet.core.network.generated.api.MonobankApi
import org.andrewdev.webwallet.core.network.generated.api.NotificationsApi
import org.andrewdev.webwallet.core.network.generated.api.PayeesApi
import org.andrewdev.webwallet.core.network.generated.api.RecurringPaymentsApi
import org.andrewdev.webwallet.core.network.generated.api.ReportsApi
import org.andrewdev.webwallet.core.network.generated.api.TagsApi
import org.andrewdev.webwallet.core.network.generated.api.TaxesApi
import org.andrewdev.webwallet.core.network.generated.api.TransactionsApi
import org.andrewdev.webwallet.core.network.generated.infrastructure.ApiClient
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Settings of the network layer the app module knows: the base URL of the flavor, the build and debug logging. */
data class NetworkConfig(
    val baseUrl: String,
    val clientInfo: ClientInfo,
    val logBodies: Boolean,
)

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun okHttpClient(config: NetworkConfig, tokens: AccessTokenProvider): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(ClientHeadersInterceptor(config.clientInfo))
        .addInterceptor(AuthInterceptor(tokens))
        .addInterceptor(IdempotencyInterceptor())
        .authenticator(TokenAuthenticator(tokens))
        .apply {
            if (config.logBodies) {
                addInterceptor(
                    HttpLoggingInterceptor(RedactingLogger { Timber.tag("HTTP").d(it) })
                        .setLevel(HttpLoggingInterceptor.Level.BODY),
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun apiClient(config: NetworkConfig, client: OkHttpClient): ApiClient =
        ApiClient(baseUrl = config.baseUrl.trimEnd('/') + "/", okHttpClientBuilder = client.newBuilder())

    @Provides @Singleton
    fun authenticationApi(client: ApiClient): AuthenticationApi = client.createService(AuthenticationApi::class.java)

    @Provides @Singleton
    fun appApi(client: ApiClient): AppApi = client.createService(AppApi::class.java)

    @Provides @Singleton
    fun currentUserApi(client: ApiClient): CurrentUserApi = client.createService(CurrentUserApi::class.java)

    @Provides @Singleton
    fun accountsApi(client: ApiClient): AccountsApi = client.createService(AccountsApi::class.java)

    @Provides @Singleton
    fun transactionsApi(client: ApiClient): TransactionsApi = client.createService(TransactionsApi::class.java)

    @Provides @Singleton
    fun categoriesApi(client: ApiClient): CategoriesApi = client.createService(CategoriesApi::class.java)

    @Provides @Singleton
    fun tagsApi(client: ApiClient): TagsApi = client.createService(TagsApi::class.java)

    @Provides @Singleton
    fun payeesApi(client: ApiClient): PayeesApi = client.createService(PayeesApi::class.java)

    @Provides @Singleton
    fun reportsApi(client: ApiClient): ReportsApi = client.createService(ReportsApi::class.java)

    @Provides @Singleton
    fun budgetApi(client: ApiClient): BudgetApi = client.createService(BudgetApi::class.java)

    @Provides @Singleton
    fun forecastApi(client: ApiClient): ForecastApi = client.createService(ForecastApi::class.java)

    @Provides @Singleton
    fun monobankApi(client: ApiClient): MonobankApi = client.createService(MonobankApi::class.java)

    @Provides @Singleton
    fun recurringApi(client: ApiClient): RecurringPaymentsApi = client.createService(RecurringPaymentsApi::class.java)

    @Provides @Singleton
    fun taxesApi(client: ApiClient): TaxesApi = client.createService(TaxesApi::class.java)

    @Provides @Singleton
    fun debtsApi(client: ApiClient): DebtsApi = client.createService(DebtsApi::class.java)

    @Provides @Singleton
    fun notificationsApi(client: ApiClient): NotificationsApi = client.createService(NotificationsApi::class.java)

    @Provides @Singleton
    fun demoApi(client: ApiClient): DemoApi = client.createService(DemoApi::class.java)
}
