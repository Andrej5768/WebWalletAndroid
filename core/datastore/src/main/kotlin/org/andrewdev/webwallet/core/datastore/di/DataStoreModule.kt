package org.andrewdev.webwallet.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.andrewdev.webwallet.core.datastore.JsonSerializer
import org.andrewdev.webwallet.core.datastore.SessionData
import org.andrewdev.webwallet.core.datastore.SettingsData
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun sessionDataStore(@ApplicationContext context: Context): DataStore<SessionData> = DataStoreFactory.create(
        serializer = JsonSerializer(SessionData.serializer(), SessionData()),
        corruptionHandler = ReplaceFileCorruptionHandler { SessionData() },
        produceFile = { context.dataStoreFile("session.json") },
    )

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<SettingsData> = DataStoreFactory.create(
        serializer = JsonSerializer(SettingsData.serializer(), SettingsData()),
        corruptionHandler = ReplaceFileCorruptionHandler { SettingsData() },
        produceFile = { context.dataStoreFile("settings.json") },
    )
}
