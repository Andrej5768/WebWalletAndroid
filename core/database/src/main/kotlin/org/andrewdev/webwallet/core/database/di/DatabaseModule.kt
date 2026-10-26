package org.andrewdev.webwallet.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.andrewdev.webwallet.core.database.AccountDao
import org.andrewdev.webwallet.core.database.CategoryDao
import org.andrewdev.webwallet.core.database.JsonCacheDao
import org.andrewdev.webwallet.core.database.PayeeDao
import org.andrewdev.webwallet.core.database.TagDao
import org.andrewdev.webwallet.core.database.TransactionDao
import org.andrewdev.webwallet.core.database.WebWalletDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** A cache can always be rebuilt, so a schema change just drops it instead of migrating. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WebWalletDatabase =
        Room.databaseBuilder(context, WebWalletDatabase::class.java, "webwallet-cache.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun accounts(db: WebWalletDatabase): AccountDao = db.accounts()

    @Provides fun categories(db: WebWalletDatabase): CategoryDao = db.categories()

    @Provides fun tags(db: WebWalletDatabase): TagDao = db.tags()

    @Provides fun payees(db: WebWalletDatabase): PayeeDao = db.payees()

    @Provides fun transactions(db: WebWalletDatabase): TransactionDao = db.transactions()

    @Provides fun jsonCache(db: WebWalletDatabase): JsonCacheDao = db.jsonCache()
}
