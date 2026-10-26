package org.andrewdev.webwallet.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id NOT IN (:ids)")
    suspend fun deleteOthers(ids: List<Long>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    /** Replaces the whole table with the backend's list, in one transaction. */
    @Transaction
    suspend fun replaceAll(accounts: List<AccountEntity>) {
        upsert(accounts)
        deleteOthers(accounts.map { it.id })
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id NOT IN (:ids)")
    suspend fun deleteOthers(ids: List<Long>)

    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        upsert(categories)
        deleteOthers(categories.map { it.id })
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id NOT IN (:ids)")
    suspend fun deleteOthers(ids: List<Long>)

    @Transaction
    suspend fun replaceAll(tags: List<TagEntity>) {
        upsert(tags)
        deleteOthers(tags.map { it.id })
    }
}

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payees ORDER BY operations DESC, name")
    fun observeAll(): Flow<List<PayeeEntity>>

    @Query("SELECT COUNT(*) FROM payees")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(payees: List<PayeeEntity>)

    @Query("DELETE FROM payees WHERE id NOT IN (:ids)")
    suspend fun deleteOthers(ids: List<Long>)

    @Transaction
    suspend fun replaceAll(payees: List<PayeeEntity>) {
        upsert(payees)
        deleteOthers(payees.map { it.id })
    }
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Upsert
    suspend fun upsert(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM feed_pages WHERE filterKey = :filterKey ORDER BY page")
    fun observePages(filterKey: String): Flow<List<FeedPageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePage(page: FeedPageEntity)

    @Query("DELETE FROM feed_pages WHERE filterKey = :filterKey AND page > :page")
    suspend fun dropPagesAfter(filterKey: String, page: Int)

    @Query("DELETE FROM feed_pages")
    suspend fun clearPages()

    /** Keeps the feed cache to the last viewed filter sets. */
    @Query(
        "DELETE FROM feed_pages WHERE filterKey NOT IN " +
            "(SELECT filterKey FROM feed_pages GROUP BY filterKey ORDER BY MAX(fetchedAt) DESC LIMIT :keep)",
    )
    suspend fun trimFilters(keep: Int)
}

@Dao
interface JsonCacheDao {
    @Query("SELECT * FROM json_cache WHERE `key` = :key")
    fun observe(key: String): Flow<JsonCacheEntity?>

    @Query("SELECT * FROM json_cache WHERE `key` = :key")
    suspend fun get(key: String): JsonCacheEntity?

    @Upsert
    suspend fun put(entry: JsonCacheEntity)

    @Query("DELETE FROM json_cache WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM json_cache WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)
}
