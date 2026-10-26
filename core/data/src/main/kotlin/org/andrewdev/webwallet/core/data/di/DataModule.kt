package org.andrewdev.webwallet.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.andrewdev.webwallet.core.auth.SessionCleanup
import org.andrewdev.webwallet.core.data.Cache
import org.andrewdev.webwallet.core.database.WebWalletDatabase

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** A sign-out wipes the cache (docs/02 §5.4). */
    @Provides
    @IntoSet
    fun clearCacheOnSignOut(database: WebWalletDatabase, cache: Cache): SessionCleanup = SessionCleanup {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        cache.forgetAll()
    }
}
