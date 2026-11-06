package org.andrewdev.webwallet.core.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.andrewdev.webwallet.core.model.BrokerImportResult
import org.andrewdev.webwallet.core.model.BrokerOperation
import org.andrewdev.webwallet.core.model.BrokerOperationStatus
import org.andrewdev.webwallet.core.model.BrokerOperationType
import org.andrewdev.webwallet.core.model.BrokerPreview
import org.andrewdev.webwallet.core.model.BrokerTicker
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.DetectedSource
import org.andrewdev.webwallet.core.model.ImportAccount
import org.andrewdev.webwallet.core.model.ImportOperation
import org.andrewdev.webwallet.core.model.ImportPreview
import org.andrewdev.webwallet.core.model.ImportResult
import org.andrewdev.webwallet.core.model.ImportSource
import org.andrewdev.webwallet.core.model.InstrumentSuggestion
import org.andrewdev.webwallet.core.model.Money
import org.andrewdev.webwallet.core.model.MonobankConnection
import org.andrewdev.webwallet.core.model.MonobankLink
import org.andrewdev.webwallet.core.model.MonobankSchedule
import org.andrewdev.webwallet.core.model.OperationEditRequest
import org.andrewdev.webwallet.core.model.ProviderAuth
import org.andrewdev.webwallet.core.model.ProviderAuthStatus
import org.andrewdev.webwallet.core.model.SyncMode
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.TransferSuggestion
import org.andrewdev.webwallet.core.model.UploadFile
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.FreedomUploadApi
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.AppApi
import org.andrewdev.webwallet.core.network.generated.api.ImportsApi
import org.andrewdev.webwallet.core.network.generated.api.MonobankApi
import org.andrewdev.webwallet.core.network.generated.model.BrokerConfirmRequest
import org.andrewdev.webwallet.core.network.generated.model.BrokerPreviewResponse
import org.andrewdev.webwallet.core.network.generated.model.ConfirmRequest
import org.andrewdev.webwallet.core.network.generated.model.ConnectRequest
import org.andrewdev.webwallet.core.network.generated.model.ConnectionResponse
import org.andrewdev.webwallet.core.network.generated.model.ImportPreviewResponse
import org.andrewdev.webwallet.core.network.generated.model.LinkRequest
import org.andrewdev.webwallet.core.network.generated.model.OperationEdit
import org.andrewdev.webwallet.core.network.generated.model.ProviderAuthResponse
import org.andrewdev.webwallet.core.network.generated.model.ProviderConnectRequest
import org.andrewdev.webwallet.core.network.generated.model.ScheduleRequest
import org.andrewdev.webwallet.core.network.generated.model.TickerChoice
import org.andrewdev.webwallet.core.network.generated.model.TokenRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.andrewdev.webwallet.core.network.generated.model.ImportResult as ImportResultDto

/** What a ticker of the broker reports becomes on confirm, when it differs from the suggestion. */
data class TickerChoiceRequest(val ticker: String, val manual: Boolean)

/**
 * Monobank connection and the file imports (docs/04 §13). Nothing here is cached: the screens ask the server each
 * time, and every change invalidates the ledger caches the other screens read.
 */
/* One method per endpoint of the imports and the Monobank connection. */
@Suppress("TooManyFunctions")
@Singleton
class ImportsRepository @Inject constructor(
    private val monobankApi: MonobankApi,
    private val importsApi: ImportsApi,
    private val freedomApi: FreedomUploadApi,
    private val appApi: AppApi,
    private val overview: OverviewRepository,
    private val accounts: AccountsRepository,
    private val transactions: TransactionsRepository,
    private val catalog: CatalogRepository,
) {
    /**
     * Previews are large and the broker's has no GET endpoint, so the last ones are held here for the preview
     * screens; after process death they are gone and the bank preview is fetched again by id.
     */
    private val bankPreviews = mutableMapOf<String, ImportPreview>()
    private val brokerPreviews = mutableMapOf<String, BrokerPreview>()

    /** Whether the server can connect Monobank through the bank app; false when the config cannot be read. */
    suspend fun monobankProviderAvailable(): Boolean =
        apiCall { appApi.config() }.valueOrNull?.features?.monobankProvider ?: false

    /** The connection, or Ok(null) when Monobank is not connected. */
    suspend fun monobank(): ApiResult<MonobankConnection?> = when (val result = apiCall { monobankApi.status() }) {
        is ApiResult.Ok -> ApiResult.Ok(result.value.toModel())
        is ApiResult.Err -> if ((result.error as? ApiError.Problem)?.status == HTTP_NOT_FOUND) {
            ApiResult.Ok(
                null,
            )
        } else {
            result
        }
    }

    suspend fun connectWithToken(
        token: String,
        since: LocalDate,
        schedule: MonobankSchedule,
    ): ApiResult<MonobankConnection> = apiCall {
        monobankApi.connect(
            ConnectRequest(
                token = token.trim(),
                since = since,
                syncMode = ConnectRequest.SyncMode.valueOf(schedule.syncMode.name),
                windowStart = schedule.windowStart.hhmm(),
                windowEnd = schedule.windowEnd.hhmm(),
                dailyAt = schedule.dailyAt.hhmm(),
            ),
        )
    }.map { it.toModel() }.onOk { ledgerChanged() }

    /** Asks the bank for an access request; the user approves it in the Monobank app at `acceptUrl`. */
    suspend fun startProviderAuth(since: LocalDate, schedule: MonobankSchedule): ApiResult<ProviderAuth> = apiCall {
        monobankApi.startProviderAuth(
            ProviderConnectRequest(
                since = since,
                syncMode = ProviderConnectRequest.SyncMode.valueOf(schedule.syncMode.name),
                windowStart = schedule.windowStart.hhmm(),
                windowEnd = schedule.windowEnd.hhmm(),
                dailyAt = schedule.dailyAt.hhmm(),
            ),
        )
    }.map { it.toModel() }

    /** The state of the access request; on approval the ledger caches are dropped, the connection exists now. */
    suspend fun providerAuthStatus(id: Long): ApiResult<ProviderAuth> =
        apiCall { monobankApi.providerAuthStatus(id) }.map { it.toModel() }.onOk {
            if (it.status == ProviderAuthStatus.APPROVED) ledgerChanged()
        }

    suspend fun saveSchedule(schedule: MonobankSchedule): ApiResult<MonobankConnection> = apiCall {
        monobankApi.schedule(
            ScheduleRequest(
                syncMode = ScheduleRequest.SyncMode.valueOf(schedule.syncMode.name),
                windowStart = schedule.windowStart.hhmm(),
                windowEnd = schedule.windowEnd.hhmm(),
                dailyAt = schedule.dailyAt.hhmm(),
            ),
        )
    }.map { it.toModel() }.onOk { overview.invalidate() }

    suspend fun syncNow(): ApiResult<MonobankConnection> =
        apiCall { monobankApi.syncNow() }.map { it.toModel() }.onOk { overview.invalidate() }

    suspend fun link(linkId: Long, accountId: Long?): ApiResult<MonobankConnection> =
        apiCall { monobankApi.link(linkId, LinkRequest(accountId)) }.map { it.toModel() }.onOk { ledgerChanged() }

    suspend fun disconnect(): ApiResult<Unit> = apiCall { monobankApi.disconnect() }.onOk { ledgerChanged() }

    // --- files

    suspend fun previewPrivatBank(file: UploadFile, accountId: Long?): ApiResult<ImportPreview> =
        apiCall { importsApi.preview(file.part("file"), accountId) }.map { remember(it.toModel()) }

    suspend fun previewMonobankPdf(file: UploadFile, accountId: Long?): ApiResult<ImportPreview> =
        apiCall { importsApi.preview1(file.part("file"), accountId) }.map { remember(it.toModel()) }

    /** Which import a shared file belongs to; UNKNOWN also when the server cannot tell or cannot be reached. */
    suspend fun detect(file: UploadFile): DetectedSource =
        apiCall { importsApi.detect(file.part("file")) }.valueOrNull
            ?.let { DetectedSource.valueOf(it.source.name) }
            ?: DetectedSource.UNKNOWN

    suspend fun previewZenMoneyToken(token: String): ApiResult<ImportPreview> =
        apiCall { importsApi.previewWithToken(TokenRequest(token.trim())) }.map { remember(it.toModel()) }

    suspend fun previewZenMoneyFile(file: UploadFile): ApiResult<ImportPreview> =
        apiCall { importsApi.previewFile(file.part("file")) }.map { remember(it.toModel()) }

    /** Page 0 comes from memory when the preview was made in this process. */
    suspend fun previewPage(id: String, page: Int): ApiResult<ImportPreview> {
        if (page == 0) bankPreviews[id]?.let { return ApiResult.Ok(it) }
        return apiCall { importsApi.get5(UUID.fromString(id), page, PAGE_SIZE) }.map { it.toModel() }
    }

    suspend fun confirm(id: String, edits: List<OperationEditRequest>): ApiResult<ImportResult> =
        apiCall {
            importsApi.confirm1(UUID.fromString(id), ConfirmRequest(edits.map { it.toDto() }), UUID.randomUUID())
        }.map { it.toModel() }.onOk {
            bankPreviews.remove(id)
            ledgerChanged()
            catalog.invalidate()
        }

    /** Drops the preview on the server; failures do not matter, it expires within the hour anyway. */
    suspend fun discard(id: String) {
        bankPreviews.remove(id)
        brokerPreviews.remove(id)
        apiCall { importsApi.discard(UUID.fromString(id)) }
    }

    suspend fun previewFreedom(files: List<UploadFile>, accountId: Long?): ApiResult<BrokerPreview> =
        apiCall { freedomApi.preview(files.map { it.part("files") }, accountId) }.map { response ->
            response.toModel().also { brokerPreviews[it.id] = it }
        }

    fun brokerPreview(id: String): BrokerPreview? = brokerPreviews[id]

    suspend fun confirmFreedom(
        id: String,
        choices: List<TickerChoiceRequest>,
        excluded: List<String>,
    ): ApiResult<BrokerImportResult> = apiCall {
        importsApi.confirm2(
            UUID.fromString(id),
            BrokerConfirmRequest(
                excluded = excluded,
                instruments = choices.map { TickerChoice(ticker = it.ticker, manual = it.manual.takeIf { m -> m }) },
            ),
            UUID.randomUUID(),
        )
    }.map {
        BrokerImportResult(it.accountId, it.accountCreated, it.operationsImported, it.duplicates, it.instrumentsCreated)
    }.onOk {
        brokerPreviews.remove(id)
        ledgerChanged()
    }

    private fun remember(preview: ImportPreview): ImportPreview = preview.also { bankPreviews[it.id] = it }

    private fun ledgerChanged() {
        overview.invalidate()
        accounts.invalidate()
        transactions.invalidateAll()
    }

    companion object {
        const val PAGE_SIZE = 100
        private const val HTTP_NOT_FOUND = 404
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private fun LocalTime.hhmm(): String = format(HHMM)
    }
}

private fun UploadFile.part(name: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(name, this.name, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))

private fun ConnectionResponse.toModel() = MonobankConnection(
    clientName = clientName,
    connectedAt = connectedAt,
    schedule = MonobankSchedule(
        syncMode = SyncMode.valueOf(syncMode.name),
        windowStart = LocalTime.parse(windowStart),
        windowEnd = LocalTime.parse(windowEnd),
        dailyAt = LocalTime.parse(dailyAt),
    ),
    lastError = lastError,
    nextRequestAt = nextRequestAt,
    nextSyncAt = nextSyncAt,
    links = accounts.map { link ->
        MonobankLink(
            id = link.id,
            title = link.title,
            currency = CurrencyCode(link.currency),
            accountId = link.accountId,
            accountName = link.accountName,
            balance = link.balance,
            bankBalance = link.bankBalance,
            difference = link.difference,
            syncedUntil = link.syncedUntil,
        )
    },
)

private fun ProviderAuthResponse.toModel() =
    ProviderAuth(id, acceptUrl, expiresAt, ProviderAuthStatus.valueOf(status.name))

private fun ImportResultDto.toModel() = ImportResult(
    transactionsImported = transactionsImported,
    transactionsMatched = transactionsMatched,
    transactionsSkipped = transactionsSkipped,
    accountsCreated = accountsCreated,
    accountsUpdated = accountsUpdated,
    categoriesCreated = categoriesCreated,
    payeesCreated = payeesCreated,
    budgetsImported = budgetsImported,
    warnings = warnings,
)

private fun ImportPreviewResponse.toModel() = ImportPreview(
    id = id.toString(),
    source = ImportSource.valueOf(source.name),
    expiresAt = expiresAt,
    summary = summary.toModel(),
    operations = operations.items.map { op ->
        ImportOperation(
            key = op.key,
            date = op.date,
            type = TransactionType.valueOf(op.type.name),
            amount = op.amount,
            currency = op.currency,
            account = ImportAccount(op.account.id, op.account.name, op.account.currency, op.account.created),
            toAccount = op.toAccount?.let { ImportAccount(it.id, it.name, it.currency, it.created) },
            payee = op.payee,
            comment = op.comment,
            categoryId = op.categoryId,
            categoryName = op.categoryName,
            tagIds = op.tagIds,
            duplicate = op.duplicate,
            transferSuggestions = op.transferSuggestions.orEmpty().map {
                TransferSuggestion(
                    accountName = it.accountName,
                    amount = it.amount,
                    currency = it.currency,
                    date = it.date,
                    difference = it.difference,
                    exact = it.exact,
                    operationKey = it.operationKey,
                    transactionId = it.transactionId,
                )
            },
        )
    },
    page = operations.page,
    totalItems = operations.totalItems,
    totalPages = operations.totalPages,
)

private fun OperationEditRequest.toDto() = OperationEdit(
    key = key,
    include = include,
    type = OperationEdit.Type.valueOf(type.name),
    transferAccountId = transferAccountId,
    keepSuggestedCategory = keepSuggestedCategory,
    categoryId = categoryId,
    tagIds = tagIds,
    comment = comment,
    rememberRule = rememberRule,
    pairKey = pairKey,
    pairTransactionId = pairTransactionId,
)

private fun BrokerPreviewResponse.toModel() = BrokerPreview(
    id = id.toString(),
    accountId = account.id,
    accountName = account.name,
    from = from,
    to = to,
    providerUnavailable = providerUnavailable,
    cash = cash.map { Money(it.amount, CurrencyCode(it.currency)) },
    instruments = instruments.map { ticker ->
        val s = ticker.suggestion
        BrokerTicker(
            ticker = ticker.ticker,
            currency = ticker.currency,
            quantityAfter = ticker.quantityAfter,
            shortfall = ticker.shortfall,
            suggestion = InstrumentSuggestion(s.instrumentId, s.symbol, s.name, s.exchange, s.currency, s.manual),
        )
    },
    operations = operations.map { op ->
        BrokerOperation(
            key = op.key,
            date = op.date,
            type = op.type?.let { BrokerOperationType.valueOf(it.name) },
            status = BrokerOperationStatus.valueOf(op.status.name),
            skip = op.skip?.name,
            ticker = op.ticker,
            description = op.description,
            quantity = op.quantity,
            price = op.price,
            amount = op.amount,
            cash = op.cash,
            fee = op.fee,
            tax = op.tax,
            currency = op.currency,
            toAmount = op.toAmount,
            toCurrency = op.toCurrency,
        )
    },
)
