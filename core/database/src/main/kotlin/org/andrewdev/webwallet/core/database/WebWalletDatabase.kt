package org.andrewdev.webwallet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** The read cache (docs/02 §5): rebuilt from the backend, wiped on sign-out, never the source of truth. */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        PayeeEntity::class,
        TransactionEntity::class,
        FeedPageEntity::class,
        JsonCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WebWalletDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun tags(): TagDao
    abstract fun payees(): PayeeDao
    abstract fun transactions(): TransactionDao
    abstract fun jsonCache(): JsonCacheDao
}
