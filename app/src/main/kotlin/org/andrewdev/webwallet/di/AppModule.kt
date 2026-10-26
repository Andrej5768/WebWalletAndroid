package org.andrewdev.webwallet.di

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.andrewdev.webwallet.BuildConfig
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.network.ClientInfo
import org.andrewdev.webwallet.core.network.di.NetworkConfig
import org.andrewdev.webwallet.feature.overview.VersionCode
import org.andrewdev.webwallet.ui.LanguageResolver
import javax.inject.Singleton

/** What only the app module knows: the flavor's base URL, the build numbers, and whether to log bodies. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun networkConfig(language: LanguageResolver): NetworkConfig = NetworkConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        clientInfo = ClientInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            androidSdk = Build.VERSION.SDK_INT,
            language = { language.current().tag },
        ),
        logBodies = BuildConfig.DEBUG,
    )

    @Provides
    fun versionCode(): VersionCode = VersionCode(BuildConfig.VERSION_CODE)

    @Provides
    @Singleton
    fun languageResolver(
        settings: SettingsStore,
    ): LanguageResolver = LanguageResolver { runBlocking { settings.current().language } }
}
