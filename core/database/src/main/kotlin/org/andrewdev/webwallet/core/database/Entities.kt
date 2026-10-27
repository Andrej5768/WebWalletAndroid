package org.andrewdev.webwallet.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An account as the backend sent it; money columns are decimal strings, never doubles. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val type: String,
    val currency: String,
    val balance: String,
    val balanceInBase: String?,
    val openingBalance: String,
    val creditLimit: String?,
    val availableCredit: String?,
    val interestRate: String?,
    val inBudget: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
    val operations: Long,
)

/** The category tree flattened; `children` is rebuilt from `parentId` when read. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val kind: String,
    val parentId: Long?,
    val icon: String?,
    val color: String?,
    val necessity: String?,
    val archived: Boolean,
    val sortOrder: Int,
    val carryOver: Boolean = false,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val color: String?,
)

@Entity(tableName = "payees")
data class PayeeEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val operations: Long,
)

/** A transaction; the splits and tag ids are small JSON arrays, the feed never filters on them. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val date: String,
    val accountId: Long,
    val amount: String,
    val currency: String,
    val toAccountId: Long?,
    val toAmount: String?,
    val toCurrency: String?,
    val categoryId: Long?,
    val payeeId: Long?,
    val payeeName: String?,
    val comment: String?,
    val tagIdsJson: String,
    val splitsJson: String,
    val originalAmount: String?,
    val originalCurrency: String?,
    val source: String,
    val adjustment: Boolean,
    val recurringId: Long?,
    val recurringName: String?,
    val createdAt: String,
)

/** One page of the feed for one filter set, so the last viewed feed can be rebuilt offline (docs/02 §5.1). */
@Entity(tableName = "feed_pages", primaryKeys = ["filterKey", "page"])
data class FeedPageEntity(
    val filterKey: String,
    val page: Int,
    val transactionIdsJson: String,
    val totalItems: Long,
    val totalPages: Int,
    val fetchedAt: Long,
)

/** Responses cached whole: the summary, the debts, the Monobank status, the profile, reports. */
@Entity(tableName = "json_cache")
data class JsonCacheEntity(
    @PrimaryKey val key: String,
    val body: String,
    val fetchedAt: Long,
)
