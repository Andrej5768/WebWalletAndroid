package org.andrewdev.webwallet.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.andrewdev.webwallet.core.database.AccountEntity
import org.andrewdev.webwallet.core.database.CategoryEntity
import org.andrewdev.webwallet.core.database.PayeeEntity
import org.andrewdev.webwallet.core.database.TagEntity
import org.andrewdev.webwallet.core.database.TransactionEntity
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.AppConfig
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.CategoryNature
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.DebtPerson
import org.andrewdev.webwallet.core.model.Named
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Split
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionSource
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.network.generated.model.AccountResponse
import org.andrewdev.webwallet.core.network.generated.model.AccountSummaryResponse
import org.andrewdev.webwallet.core.network.generated.model.AppConfigResponse
import org.andrewdev.webwallet.core.network.generated.model.CategoryResponse
import org.andrewdev.webwallet.core.network.generated.model.NamedResponse
import org.andrewdev.webwallet.core.network.generated.model.Person
import org.andrewdev.webwallet.core.network.generated.model.TagResponse
import org.andrewdev.webwallet.core.network.generated.model.TransactionResponse
import org.andrewdev.webwallet.core.network.generated.model.UserResponse
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/** DTO → entity → model, as pure functions (docs/02 §3). Money travels as decimal strings through Room. */
internal val json = Json { ignoreUnknownKeys = true }

// --- accounts

fun AccountResponse.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type.value,
    currency = currency,
    balance = balance.toPlainString(),
    balanceInBase = balanceInBase?.toPlainString(),
    openingBalance = openingBalance.toPlainString(),
    creditLimit = creditLimit?.toPlainString(),
    availableCredit = availableCredit?.toPlainString(),
    interestRate = interestRate?.toPlainString(),
    inBudget = inBudget,
    archived = archived,
    sortOrder = sortOrder,
    operations = operations,
)

fun AccountEntity.toModel(): Account = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    currency = CurrencyCode(currency),
    balance = BigDecimal(balance),
    balanceInBase = balanceInBase?.let(::BigDecimal),
    openingBalance = BigDecimal(openingBalance),
    creditLimit = creditLimit?.let(::BigDecimal),
    availableCredit = availableCredit?.let(::BigDecimal),
    interestRate = interestRate?.let(::BigDecimal),
    inBudget = inBudget,
    archived = archived,
    sortOrder = sortOrder,
    operations = operations,
)

fun AccountSummaryResponse.toModel(): AccountSummary = AccountSummary(
    currency = CurrencyCode(currency),
    netWorth = netWorth,
    available = available,
    availableWithCredit = availableWithCredit,
    readyToAssign = readyToAssign,
    unconvertedCurrencies = unconvertedCurrencies,
)

/** The summary as it is cached whole in `json_cache`. */
@Serializable
data class SummaryJson(
    val currency: String,
    val netWorth: String,
    val available: String,
    val availableWithCredit: String,
    val readyToAssign: String,
    val unconvertedCurrencies: List<String>,
) {
    fun toModel(): AccountSummary = AccountSummary(
        CurrencyCode(currency),
        BigDecimal(netWorth),
        BigDecimal(available),
        BigDecimal(availableWithCredit),
        BigDecimal(readyToAssign),
        unconvertedCurrencies,
    )

    companion object {
        fun of(response: AccountSummaryResponse) = SummaryJson(
            response.currency,
            response.netWorth.toPlainString(),
            response.available.toPlainString(),
            response.availableWithCredit.toPlainString(),
            response.readyToAssign.toPlainString(),
            response.unconvertedCurrencies,
        )
    }
}

// --- catalog

fun CategoryResponse.flattenToEntities(): List<CategoryEntity> = listOf(
    CategoryEntity(
        id = id,
        name = name,
        kind = kind.value,
        parentId = parentId,
        icon = icon,
        color = color?.value,
        necessity = necessity?.value,
        archived = archived,
        sortOrder = sortOrder,
    ),
) + children.flatMap { it.flattenToEntities() }

/** Rebuilds the two-level tree the backend sends from the flat rows. */
fun List<CategoryEntity>.toTree(): List<Category> {
    val byParent = groupBy { it.parentId }
    fun build(entity: CategoryEntity): Category = Category(
        id = entity.id,
        name = entity.name,
        kind = CategoryKind.valueOf(entity.kind),
        parentId = entity.parentId,
        icon = entity.icon,
        color = entity.color,
        nature = entity.necessity?.let { CategoryNature.valueOf(it) } ?: CategoryNature.INHERIT,
        archived = entity.archived,
        sortOrder = entity.sortOrder,
        children = byParent[entity.id].orEmpty().map(::build),
    )
    return byParent[null].orEmpty().map(::build)
}

fun TagResponse.toEntity(): TagEntity = TagEntity(id, name, color?.value)

fun TagEntity.toModel(): Tag = Tag(id, name, color)

fun NamedResponse.toPayeeEntity(operations: Long = 0): PayeeEntity = PayeeEntity(id, name, operations)

fun PayeeEntity.toModel(): Payee = Payee(id, name, operations)

// --- transactions

@Serializable
internal data class SplitJson(val id: Long?, val amount: String, val categoryId: Long?, val comment: String?)

fun TransactionResponse.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type.value,
    date = date.toString(),
    accountId = accountId,
    amount = amount.toPlainString(),
    currency = currency,
    toAccountId = toAccountId,
    toAmount = toAmount?.toPlainString(),
    toCurrency = toCurrency,
    // The backend keeps the category in the splits: one split is a plain category, more are a split
    categoryId = splits.singleOrNull()?.categoryId,
    payeeId = payee?.id,
    payeeName = payee?.name,
    comment = comment,
    tagIdsJson = json.encodeToString(ListSerializer(Long.serializer()), tagIds),
    splitsJson = json.encodeToString(
        ListSerializer(SplitJson.serializer()),
        splits.map { SplitJson(it.id, it.amount.toPlainString(), it.categoryId, it.comment) },
    ),
    originalAmount = originalAmount?.toPlainString(),
    originalCurrency = originalCurrency,
    source = source.value,
    adjustment = adjustment,
    recurringId = recurring?.id,
    recurringName = recurring?.name,
    createdAt = createdAt.toString(),
)

/** A saved transaction back into the cache after a write. */
fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type.name,
    date = date.toString(),
    accountId = accountId,
    amount = amount.toPlainString(),
    currency = currency.code,
    toAccountId = toAccountId,
    toAmount = toAmount?.toPlainString(),
    toCurrency = toCurrency?.code,
    categoryId = categoryId,
    payeeId = payee?.id,
    payeeName = payee?.name,
    comment = comment,
    tagIdsJson = json.encodeToString(ListSerializer(Long.serializer()), tagIds),
    splitsJson = json.encodeToString(
        ListSerializer(SplitJson.serializer()),
        splits.map { SplitJson(it.id, it.amount.toPlainString(), it.categoryId, it.comment) },
    ),
    originalAmount = originalAmount?.toPlainString(),
    originalCurrency = originalCurrency?.code,
    source = source.name,
    adjustment = adjustment,
    recurringId = recurring?.id,
    recurringName = recurring?.name,
    createdAt = createdAt.toString(),
)

fun TransactionEntity.toModel(): Transaction = Transaction(
    id = id,
    type = TransactionType.valueOf(type),
    date = LocalDate.parse(date),
    accountId = accountId,
    amount = BigDecimal(amount),
    currency = CurrencyCode(currency),
    toAccountId = toAccountId,
    toAmount = toAmount?.let(::BigDecimal),
    toCurrency = toCurrency?.let(::CurrencyCode),
    categoryId = categoryId,
    payee = payeeId?.let { Named(it, payeeName.orEmpty()) },
    comment = comment,
    tagIds = json.decodeFromString(ListSerializer(Long.serializer()), tagIdsJson),
    splits = json.decodeFromString(ListSerializer(SplitJson.serializer()), splitsJson)
        .map { Split(it.id, BigDecimal(it.amount), it.categoryId, it.comment) },
    originalAmount = originalAmount?.let(::BigDecimal),
    originalCurrency = originalCurrency?.let(::CurrencyCode),
    source = TransactionSource.valueOf(source),
    adjustment = adjustment,
    recurring = recurringId?.let { Named(it, recurringName.orEmpty()) },
    createdAt = OffsetDateTime.parse(createdAt),
)

// --- user, debts, config

fun UserResponse.toModel(): User = User(
    id = id,
    email = email,
    emailVerified = emailVerified,
    firstName = firstName,
    lastName = lastName,
    baseCurrency = CurrencyCode(baseCurrency),
    locale = locale,
    hasPassword = hasPassword,
    twoFactorEnabled = twoFactorEnabled,
    recoveryCodesLeft = recoveryCodesLeft,
    googleEmail = googleEmail,
    demo = demo,
    demoExpiresAt = demoExpiresAt?.toInstant(),
)

/** The profile as cached in `json_cache`. */
@Serializable
data class UserJson(
    val id: Long,
    val email: String,
    val emailVerified: Boolean,
    val firstName: String,
    val lastName: String?,
    val baseCurrency: String,
    val locale: String,
    val hasPassword: Boolean,
    val twoFactorEnabled: Boolean,
    val recoveryCodesLeft: Long,
    val googleEmail: String?,
    val demo: Boolean = false,
    val demoExpiresAt: String? = null,
) {
    fun toModel() = User(
        id, email, emailVerified, firstName, lastName, CurrencyCode(baseCurrency), locale, hasPassword,
        twoFactorEnabled, recoveryCodesLeft, googleEmail, demo, demoExpiresAt?.let(java.time.Instant::parse),
    )

    companion object {
        fun of(user: User) = UserJson(
            user.id, user.email, user.emailVerified, user.firstName, user.lastName, user.baseCurrency.code,
            user.locale, user.hasPassword, user.twoFactorEnabled, user.recoveryCodesLeft, user.googleEmail,
            user.demo, user.demoExpiresAt?.toString(),
        )
    }
}

@Serializable
data class DebtsJson(val accountId: Long?, val currency: String, val people: List<PersonJson>) {
    fun toModel(): List<DebtPerson> = people.map {
        DebtPerson(
            payeeId = it.payeeId ?: 0,
            name = it.name,
            balance = BigDecimal(it.balance),
            currency = CurrencyCode(currency),
            operations = it.operations,
            lastDate = it.lastDate?.let(LocalDate::parse),
        )
    }
}

@Serializable
data class PersonJson(
    val payeeId: Long?,
    val name: String?,
    val balance: String,
    val operations: Long,
    val lastDate: String?,
)

fun Person.toJson() = PersonJson(payeeId, name, balance.toPlainString(), operations.toLong(), lastDate.toString())

fun AppConfigResponse.toModel(): AppConfig = AppConfig(
    minSupportedVersionCode = android.minSupportedVersionCode,
    latestVersionCode = android.latestVersionCode,
    storeUrl = android.storeUrl,
    googleSignIn = features.googleSignIn,
    marketSearch = features.marketSearch,
    telegram = features.telegram,
    push = features.push,
    demo = features.demo,
)
