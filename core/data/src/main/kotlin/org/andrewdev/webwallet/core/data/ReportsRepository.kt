package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.andrewdev.webwallet.core.model.Anomaly
import org.andrewdev.webwallet.core.model.CategoryChange
import org.andrewdev.webwallet.core.model.CategorySpending
import org.andrewdev.webwallet.core.model.CategoryTrend
import org.andrewdev.webwallet.core.model.CompareDay
import org.andrewdev.webwallet.core.model.CompareReport
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.IncomeExpenseReport
import org.andrewdev.webwallet.core.model.MonthFigures
import org.andrewdev.webwallet.core.model.MonthNetWorth
import org.andrewdev.webwallet.core.model.MonthSavings
import org.andrewdev.webwallet.core.model.NeedsWantsPart
import org.andrewdev.webwallet.core.model.NeedsWantsReport
import org.andrewdev.webwallet.core.model.NetWorthReport
import org.andrewdev.webwallet.core.model.PayeeSpending
import org.andrewdev.webwallet.core.model.PayeesReport
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.SavingsDestination
import org.andrewdev.webwallet.core.model.SavingsReport
import org.andrewdev.webwallet.core.model.SpendingReport
import org.andrewdev.webwallet.core.model.TrendsReport
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.AnalyticsApi
import org.andrewdev.webwallet.core.network.generated.api.ReportsApi
import org.andrewdev.webwallet.core.network.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.andrewdev.webwallet.core.network.generated.model.CategorySpending as CategorySpendingDto
import org.andrewdev.webwallet.core.network.generated.model.CompareResponse as CompareResponseDto
import org.andrewdev.webwallet.core.network.generated.model.IncomeExpenseResponse as IncomeExpenseResponseDto
import org.andrewdev.webwallet.core.network.generated.model.NeedsWantsResponse as NeedsWantsResponseDto
import org.andrewdev.webwallet.core.network.generated.model.NetWorthResponse as NetWorthResponseDto
import org.andrewdev.webwallet.core.network.generated.model.Part as PartDto
import org.andrewdev.webwallet.core.network.generated.model.PayeesResponse as PayeesResponseDto
import org.andrewdev.webwallet.core.network.generated.model.SavingsResponse as SavingsResponseDto
import org.andrewdev.webwallet.core.network.generated.model.SpendingResponse as SpendingResponseDto
import org.andrewdev.webwallet.core.network.generated.model.TrendsResponse as TrendsResponseDto

/**
 * The reports and analytics (docs/04 §9), each cached in `json_cache` under its endpoint and parameters as a small
 * JSON tree of the model, so that a report opens with its last numbers while the fresh ones load.
 */
@Singleton
class ReportsRepository @Inject constructor(
    private val reportsApi: ReportsApi,
    private val analyticsApi: AnalyticsApi,
    private val jsonCache: JsonCache,
    private val cache: Cache,
) {
    fun spending(period: Period): Flow<CacheState<SpendingReport>> = observe(
        key = key("spending", period),
        read = { it.toSpending() },
        fetch = { apiCall { reportsApi.spending(period.from, period.to) }.map { response -> spendingJson(response) } },
    )

    fun incomeExpense(period: Period): Flow<CacheState<IncomeExpenseReport>> = observe(
        key = key("income-expense", period),
        read = { it.toIncomeExpense() },
        fetch = {
            apiCall { reportsApi.incomeExpense(period.from, period.to) }.map { response -> incomeExpenseJson(response) }
        },
    )

    fun netWorth(period: Period): Flow<CacheState<NetWorthReport>> = observe(
        key = key("net-worth", period),
        read = { it.toNetWorth() },
        fetch = { apiCall { reportsApi.netWorth(period.from, period.to) }.map { response -> netWorthJson(response) } },
    )

    fun payees(period: Period): Flow<CacheState<PayeesReport>> = observe(
        key = key("payees", period),
        read = { it.toPayees() },
        fetch = {
            apiCall { reportsApi.payees(period.from, period.to, PAYEES_LIMIT) }.map { response -> payeesJson(response) }
        },
    )

    fun compare(fromA: LocalDate, toA: LocalDate, fromB: LocalDate, toB: LocalDate): Flow<CacheState<CompareReport>> =
        observe(
            key = "analytics:compare:$fromA|$toA|$fromB|$toB",
            read = { it.toCompare() },
            fetch = {
                apiCall {
                    analyticsApi.compare(
                        fromA,
                        toA,
                        fromB,
                        toB,
                    )
                }.map { response -> compareJson(response) }
            },
        )

    /** [from] and [to] are months, `yyyy-MM`. */
    fun trends(from: String, to: String): Flow<CacheState<TrendsReport>> = observe(
        key = "analytics:trends:$from|$to",
        read = { it.toTrends() },
        fetch = { apiCall { analyticsApi.trends(from, to) }.map { response -> trendsJson(response) } },
    )

    fun savings(from: String, to: String, target: BigDecimal): Flow<CacheState<SavingsReport>> = observe(
        key = "analytics:savings:$from|$to|${target.toPlainString()}",
        read = { it.toSavings() },
        fetch = { apiCall { analyticsApi.savings(from, to, target) }.map { response -> savingsJson(response) } },
    )

    fun needsWants(period: Period): Flow<CacheState<NeedsWantsReport>> = observe(
        key = key("needs-wants", period),
        read = { it.toNeedsWants() },
        fetch = {
            apiCall { analyticsApi.needsWants(period.from, period.to) }.map { response -> needsWantsJson(response) }
        },
    )

    fun invalidateAll() = cache.invalidatePrefix(PREFIX)

    private fun <T : Any> observe(
        key: String,
        read: (JsonObject) -> T,
        fetch: suspend () -> ApiResult<JsonObject>,
    ): Flow<CacheState<T>> = cache.observe(
        key = key,
        ttl = Cache.HOUR,
        local = jsonCache.observe(key, JsonObject.serializer()).map { it?.let(read) },
        fetch = { fetch().map { json -> jsonCache.put(key, JsonObject.serializer(), json) } },
    )

    private fun key(name: String, period: Period) = "$PREFIX$name:${period.from}|${period.to}"

    companion object {
        const val PREFIX = "reports:"
        private const val PAYEES_LIMIT = 50
    }
}

// --- the JSON trees: numbers as strings, so BigDecimal survives the round trip exactly

private fun money(value: BigDecimal?): JsonElement = value?.let {
    JsonPrimitive(it.toPlainString())
} ?: JsonPrimitive(null as String?)

private fun strings(values: List<String>) = JsonArray(values.map { JsonPrimitive(it) })

private fun JsonElement.big(): BigDecimal = BigDecimal(jsonPrimitive.content)

private fun JsonElement.bigOrNull(): BigDecimal? = jsonPrimitive.contentOrNullSafe()?.let(::BigDecimal)

private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content

private fun JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

private fun JsonObject.strOrNull(name: String): String? = get(name)?.jsonPrimitive?.contentOrNullSafe()

private fun JsonObject.list(name: String): List<JsonObject> = getValue(name).jsonArray.map { it.jsonObject }

private fun JsonObject.stringList(name: String): List<String> = getValue(
    name,
).jsonArray.map { it.jsonPrimitive.content }

private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.toMap())

private fun spendingJson(r: SpendingResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "from" to JsonPrimitive(r.from.toString()),
    "to" to JsonPrimitive(r.to.toString()),
    "total" to money(r.total),
    "uncategorized" to money(r.uncategorized),
    "categories" to JsonArray(r.categories.map { categorySpendingJson(it) }),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun categorySpendingJson(c: CategorySpendingDto): JsonObject = obj(
    "categoryId" to JsonPrimitive(c.categoryId),
    "name" to JsonPrimitive(c.name),
    "amount" to money(c.amount),
    "share" to money(c.share),
    "children" to JsonArray(c.children.map { categorySpendingJson(it) }),
)

private fun JsonObject.toSpending() = SpendingReport(
    currency = CurrencyCode(str("currency")),
    from = LocalDate.parse(str("from")),
    to = LocalDate.parse(str("to")),
    total = getValue("total").big(),
    uncategorized = getValue("uncategorized").big(),
    categories = list("categories").map { it.toCategorySpending() },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun JsonObject.toCategorySpending(): CategorySpending = CategorySpending(
    categoryId = str("categoryId").toLong(),
    name = str("name"),
    amount = getValue("amount").big(),
    share = getValue("share").big(),
    children = list("children").map { it.toCategorySpending() },
)

private fun incomeExpenseJson(r: IncomeExpenseResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "income" to money(r.income),
    "expense" to money(r.expense),
    "net" to money(r.net),
    "savingsRate" to money(r.savingsRate),
    "months" to JsonArray(
        r.months.map {
            obj(
                "month" to JsonPrimitive(it.month),
                "income" to money(it.income),
                "expense" to money(it.expense),
                "net" to money(it.net),
                "savingsRate" to money(it.savingsRate),
            )
        },
    ),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun JsonObject.toIncomeExpense() = IncomeExpenseReport(
    currency = CurrencyCode(str("currency")),
    income = getValue("income").big(),
    expense = getValue("expense").big(),
    net = getValue("net").big(),
    savingsRate = getValue("savingsRate").bigOrNull(),
    months = list("months").map {
        MonthFigures(
            it.str("month"),
            it.getValue("income").big(),
            it.getValue("expense").big(),
            it.getValue("net").big(),
            it.getValue("savingsRate").bigOrNull(),
        )
    },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun netWorthJson(r: NetWorthResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "months" to JsonArray(
        r.months.map {
            obj(
                "month" to JsonPrimitive(it.month),
                "assets" to money(it.assets),
                "liabilities" to money(it.liabilities),
                "netWorth" to money(it.netWorth),
            )
        },
    ),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun JsonObject.toNetWorth() = NetWorthReport(
    currency = CurrencyCode(str("currency")),
    months = list("months").map {
        MonthNetWorth(
            it.str("month"),
            it.getValue("assets").big(),
            it.getValue("liabilities").big(),
            it.getValue("netWorth").big(),
        )
    },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun payeesJson(r: PayeesResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "payees" to JsonArray(
        r.payees.map {
            obj(
                "payeeId" to JsonPrimitive(it.payeeId),
                "name" to JsonPrimitive(it.name),
                "amount" to money(it.amount),
                "transactions" to JsonPrimitive(it.transactions),
            )
        },
    ),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun JsonObject.toPayees() = PayeesReport(
    currency = CurrencyCode(str("currency")),
    payees = list("payees").map {
        PayeeSpending(
            it.str("payeeId").toLong(),
            it.str("name"),
            it.getValue("amount").big(),
            it.str("transactions").toInt(),
        )
    },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun compareJson(r: CompareResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "fromA" to JsonPrimitive(r.fromA.toString()),
    "toA" to JsonPrimitive(r.toA.toString()),
    "fromB" to JsonPrimitive(r.fromB.toString()),
    "toB" to JsonPrimitive(r.toB.toString()),
    "totalA" to money(r.totalA),
    "totalB" to money(r.totalB),
    "change" to money(r.change),
    "changeShare" to money(r.changeShare),
    "days" to JsonArray(
        r.days.map { obj("day" to JsonPrimitive(it.day), "a" to money(it.a), "b" to money(it.b)) },
    ),
    "categories" to JsonArray(
        r.categories.map {
            obj(
                "categoryId" to JsonPrimitive(it.categoryId),
                "name" to JsonPrimitive(it.name),
                "a" to money(it.a),
                "b" to money(it.b),
                "change" to money(it.change),
                "changeShare" to money(it.changeShare),
            )
        },
    ),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun JsonObject.toCompare() = CompareReport(
    currency = CurrencyCode(str("currency")),
    fromA = LocalDate.parse(str("fromA")),
    toA = LocalDate.parse(str("toA")),
    fromB = LocalDate.parse(str("fromB")),
    toB = LocalDate.parse(str("toB")),
    totalA = getValue("totalA").big(),
    totalB = getValue("totalB").big(),
    change = getValue("change").big(),
    changeShare = getValue("changeShare").bigOrNull(),
    days = list(
        "days",
    ).map { CompareDay(it.str("day").toInt(), it.getValue("a").bigOrNull(), it.getValue("b").bigOrNull()) },
    categories = list("categories").map {
        CategoryChange(
            it.strOrNull("categoryId")?.toLong(),
            it.strOrNull("name"),
            it.getValue("a").big(),
            it.getValue("b").big(),
            it.getValue("change").big(),
            it.getValue("changeShare").bigOrNull(),
        )
    },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun trendsJson(r: TrendsResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "from" to JsonPrimitive(r.from),
    "to" to JsonPrimitive(r.to),
    "months" to strings(r.months.map { it.month }),
    "average" to money(r.average),
    "categories" to JsonArray(
        r.categories.map { c ->
            obj(
                "categoryId" to JsonPrimitive(c.categoryId),
                "name" to JsonPrimitive(c.name),
                "amounts" to strings(c.amounts.map { it.toPlainString() }),
                "total" to money(c.total),
                "average" to money(c.average),
                "anomalies" to JsonArray(
                    c.anomalies.map {
                        obj(
                            "month" to JsonPrimitive(it.month),
                            "amount" to money(it.amount),
                            "average" to money(it.average),
                            "ratio" to money(it.ratio),
                        )
                    },
                ),
            )
        },
    ),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun JsonObject.toTrends() = TrendsReport(
    currency = CurrencyCode(str("currency")),
    from = str("from"),
    to = str("to"),
    months = stringList("months"),
    average = getValue("average").big(),
    categories = list("categories").map { c ->
        CategoryTrend(
            categoryId = c.strOrNull("categoryId")?.toLong(),
            name = c.strOrNull("name"),
            amounts = c.stringList("amounts").map(::BigDecimal),
            total = c.getValue("total").big(),
            average = c.getValue("average").big(),
            anomalies = c.list("anomalies").map {
                Anomaly(
                    it.str("month"),
                    it.getValue("amount").big(),
                    it.getValue("average").big(),
                    it.getValue("ratio").big(),
                )
            },
        )
    },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun savingsJson(r: SavingsResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "target" to money(r.target),
    "averageRate" to money(r.averageRate),
    "totalSaved" to money(r.totalSaved),
    "bestMonth" to JsonPrimitive(r.bestMonth),
    "months" to JsonArray(
        r.months.map { m ->
            obj(
                "month" to JsonPrimitive(m.month),
                "income" to money(m.income),
                "expense" to money(m.expense),
                "net" to money(m.net),
                "savingsRate" to money(m.savingsRate),
                "cumulative" to money(m.cumulative),
                "complete" to JsonPrimitive(m.complete),
                "destinations" to JsonArray(
                    m.destinations.map {
                        obj(
                            "accountId" to JsonPrimitive(it.accountId),
                            "name" to JsonPrimitive(it.name),
                            "amount" to money(it.amount),
                        )
                    },
                ),
            )
        },
    ),
    "unconverted" to strings(r.unconvertedCurrencies),
)

private fun JsonObject.toSavings() = SavingsReport(
    currency = CurrencyCode(str("currency")),
    target = getValue("target").big(),
    averageRate = getValue("averageRate").bigOrNull(),
    totalSaved = getValue("totalSaved").big(),
    bestMonth = strOrNull("bestMonth"),
    months = list("months").map { m ->
        MonthSavings(
            month = m.str("month"),
            income = m.getValue("income").big(),
            expense = m.getValue("expense").big(),
            net = m.getValue("net").big(),
            savingsRate = m.getValue("savingsRate").bigOrNull(),
            cumulative = m.getValue("cumulative").big(),
            complete = m.str("complete").toBoolean(),
            destinations = m.list("destinations").map {
                SavingsDestination(it.str("accountId").toLong(), it.str("name"), it.getValue("amount").big())
            },
        )
    },
    unconvertedCurrencies = stringList("unconverted"),
)

private fun partJson(p: PartDto) =
    obj("amount" to money(p.amount), "share" to money(p.share), "target" to money(p.target))

private fun JsonObject.toPart() = NeedsWantsPart(
    getValue("amount").big(),
    getValue("share").bigOrNull(),
    getValue("target").bigOrNull(),
)

private fun needsWantsJson(r: NeedsWantsResponseDto): JsonObject = obj(
    "currency" to JsonPrimitive(r.currency),
    "income" to money(r.income),
    "needs" to partJson(r.needs),
    "wants" to partJson(r.wants),
    "savings" to partJson(r.savings),
    "unclassified" to partJson(r.unclassified),
)

private fun JsonObject.toNeedsWants() = NeedsWantsReport(
    currency = CurrencyCode(str("currency")),
    income = getValue("income").big(),
    needs = getValue("needs").jsonObject.toPart(),
    wants = getValue("wants").jsonObject.toPart(),
    savings = getValue("savings").jsonObject.toPart(),
    unclassified = getValue("unclassified").jsonObject.toPart(),
)
