package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** How often Monobank is asked for new operations; WEBHOOK is only shown for old connections that still have it. */
enum class SyncMode { INTERVAL, DAILY, WEBHOOK, MANUAL }

/** The sync schedule of a Monobank connection, with the web's defaults for a new one. */
data class MonobankSchedule(
    val syncMode: SyncMode = SyncMode.INTERVAL,
    val windowStart: LocalTime = DEFAULT_WINDOW_START,
    val windowEnd: LocalTime = DEFAULT_WINDOW_END,
    val dailyAt: LocalTime = DEFAULT_DAILY_AT,
) {
    private companion object {
        val DEFAULT_WINDOW_START: LocalTime = LocalTime.parse("08:00")
        val DEFAULT_WINDOW_END: LocalTime = LocalTime.parse("23:00")
        val DEFAULT_DAILY_AT: LocalTime = LocalTime.parse("06:00")
    }
}

/** A card or jar of the bank and the ledger account it fills, if any. */
data class MonobankLink(
    val id: Long,
    val title: String,
    val currency: CurrencyCode,
    val accountId: Long?,
    val accountName: String?,
    /** Balance of the linked account in the ledger. */
    val balance: BigDecimal?,
    val bankBalance: BigDecimal?,
    /** Bank minus ledger; positive means income is missing in the ledger. */
    val difference: BigDecimal?,
    val syncedUntil: OffsetDateTime?,
)

/** `GET /banks/monobank`: the connection, its schedule and its cards. */
data class MonobankConnection(
    val clientName: String,
    val connectedAt: OffsetDateTime,
    val schedule: MonobankSchedule,
    val lastError: String?,
    /** The bank allows one request a minute; Sync now waits for this moment. */
    val nextRequestAt: OffsetDateTime?,
    /** When the next synchronization is due; in the past while one is running. */
    val nextSyncAt: OffsetDateTime?,
    val links: List<MonobankLink>,
)

enum class ProviderAuthStatus { PENDING, APPROVED, EXPIRED }

/** An access request to approve in the Monobank app. */
data class ProviderAuth(
    val id: Long,
    val acceptUrl: String,
    val expiresAt: OffsetDateTime,
    val status: ProviderAuthStatus,
)

enum class ImportSource { PRIVATBANK, ZENMONEY, FREEDOM }

/** A file picked by the user, read into memory for the upload. */
class UploadFile(val name: String, val mimeType: String, val bytes: ByteArray)

/** What an import did or would do. */
data class ImportResult(
    val transactionsImported: Int,
    val transactionsMatched: Int,
    val transactionsSkipped: Int,
    val accountsCreated: Int,
    val accountsUpdated: Int,
    val categoriesCreated: Int,
    val payeesCreated: Int,
    val budgetsImported: Int,
    val warnings: List<String>,
)

data class ImportAccount(val id: Long?, val name: String, val currency: String, val created: Boolean)

/** The other half of a transfer: an operation of the same file or a transaction already in the ledger. */
data class TransferSuggestion(
    val accountName: String,
    val amount: BigDecimal,
    val currency: String,
    val date: LocalDate,
    val difference: BigDecimal,
    val exact: Boolean,
    val operationKey: String?,
    val transactionId: Long?,
)

/** One operation of a previewed file. */
data class ImportOperation(
    val key: String,
    val date: LocalDate,
    val type: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    val account: ImportAccount,
    val toAccount: ImportAccount?,
    val payee: String?,
    val comment: String?,
    val categoryId: Long?,
    /** A category the import would create when [categoryId] is null. */
    val categoryName: String?,
    val tagIds: List<Long>,
    val duplicate: Boolean,
    val transferSuggestions: List<TransferSuggestion>,
)

/** One page of a preview of a bank statement or a Zen Money export. */
data class ImportPreview(
    val id: String,
    val source: ImportSource,
    val expiresAt: OffsetDateTime,
    val summary: ImportResult,
    val operations: List<ImportOperation>,
    val page: Int,
    val totalItems: Long,
    val totalPages: Int,
)

/** What the user changed in one row; a listed row gets exactly these values. */
data class OperationEditRequest(
    val key: String,
    val include: Boolean,
    val type: TransactionType,
    val transferAccountId: Long?,
    val keepSuggestedCategory: Boolean,
    val categoryId: Long?,
    val tagIds: List<Long>,
    val comment: String?,
    val rememberRule: Boolean,
    val pairKey: String?,
    val pairTransactionId: Long?,
)

// --- broker reports (Freedom Finance)

enum class BrokerOperationType { BUY, SELL, DIVIDEND, COUPON, FEE, SPLIT, DEPOSIT, WITHDRAWAL, EXCHANGE }

enum class BrokerOperationStatus { NEW, DUPLICATE, SKIPPED }

/** One row of the broker reports. */
data class BrokerOperation(
    val key: String,
    val date: LocalDate,
    /** Null for a row the import skips; [skip] says why. */
    val type: BrokerOperationType?,
    val status: BrokerOperationStatus,
    val skip: String?,
    val ticker: String?,
    val description: String,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal,
    /** The change of the cash balance in [currency]. */
    val cash: BigDecimal,
    val fee: BigDecimal,
    val tax: BigDecimal,
    val currency: String,
    val toAmount: BigDecimal?,
    val toCurrency: String?,
)

/** The instrument the import would use for a ticker. */
data class InstrumentSuggestion(
    val instrumentId: Long?,
    val symbol: String,
    val name: String,
    val exchange: String?,
    val currency: String,
    /** No instrument was found: one is created by hand. */
    val manual: Boolean,
)

data class BrokerTicker(
    val ticker: String,
    val currency: String,
    val quantityAfter: BigDecimal,
    val shortfall: Boolean,
    val suggestion: InstrumentSuggestion,
)

data class BrokerPreview(
    val id: String,
    val accountId: Long?,
    val accountName: String,
    val from: LocalDate?,
    val to: LocalDate?,
    val providerUnavailable: Boolean,
    val cash: List<Money>,
    val instruments: List<BrokerTicker>,
    val operations: List<BrokerOperation>,
)

data class BrokerImportResult(
    val accountId: Long,
    val accountCreated: Boolean,
    val operationsImported: Int,
    val duplicates: Int,
    val instrumentsCreated: Int,
)
