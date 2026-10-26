@file:Suppress("MatchingDeclarationName")

package org.andrewdev.webwallet.feature.accounts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountGroup
import org.andrewdev.webwallet.core.model.AccountSummary
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.DebtPerson
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.AccountTypeIcon
import org.andrewdev.webwallet.core.ui.Amount
import org.andrewdev.webwallet.core.ui.AmountTone
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.Figure
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.StaleBanner
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.accounts.AccountGroupRow
import org.andrewdev.webwallet.feature.accounts.AccountsUiState
import org.andrewdev.webwallet.feature.accounts.AccountsViewModel
import org.andrewdev.webwallet.feature.accounts.R
import java.math.BigDecimal

/** Where the accounts screen sends the user. */
class AccountsActions(
    val openEditor: (id: Long?) -> Unit,
    val openTransactions: (accountId: Long) -> Unit,
    val openPayee: (payeeId: Long, name: String) -> Unit,
    val newTransaction: (accountId: Long, type: TransactionType) -> Unit,
)

@Composable
fun AccountsRoute(actions: AccountsActions, viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    AccountsScreen(
        state = state,
        snackbar = snackbar,
        actions = actions,
        onRefresh = viewModel::refresh,
        onToggleArchived = viewModel::toggleArchived,
        onToggleGroup = viewModel::toggleGroup,
        onSetArchived = { account, archived -> viewModel.setArchived(account, archived, showError) },
        onSyncMonobank = { viewModel.syncMonobank(showError) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    snackbar: SnackbarHostState,
    actions: AccountsActions,
    onRefresh: () -> Unit,
    onToggleArchived: () -> Unit,
    onToggleGroup: (AccountGroup) -> Unit,
    onSetArchived: (Account, Boolean) -> Unit,
    onSyncMonobank: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<Account?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_accounts_title)) },
                actions = {
                    IconButton(onClick = { actions.openEditor(null) }) {
                        Icon(Lucide.Plus, contentDescription = stringResource(R.string.feature_accounts_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                AccountsUiState.Loading -> SkeletonList(rows = 5)
                is AccountsUiState.Error -> ErrorState(state.error, onRetry = onRefresh)
                is AccountsUiState.Content -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                    AccountsContent(
                        state,
                        actions,
                        onToggleArchived,
                        onToggleGroup,
                        onSyncMonobank,
                        onRefresh,
                        onMenu = { menuFor = it },
                    )
                }
            }
        }
    }
    menuFor?.let { account ->
        BottomSheetMenu(
            title = account.name,
            items = listOfNotNull(
                MenuItem(stringResource(R.string.feature_accounts_menu_edit)) { actions.openEditor(account.id) },
                MenuItem(
                    stringResource(R.string.feature_accounts_menu_transactions),
                ) { actions.openTransactions(account.id) },
                MenuItem(stringResource(R.string.feature_accounts_menu_new_transaction)) {
                    actions.newTransaction(account.id, TransactionType.EXPENSE)
                },
                MenuItem(
                    stringResource(
                        if (account.archived) {
                            R.string.feature_accounts_menu_unarchive
                        } else {
                            R.string.feature_accounts_menu_archive
                        },
                    ),
                ) { onSetArchived(account, !account.archived) },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun AccountsContent(
    state: AccountsUiState.Content,
    actions: AccountsActions,
    onToggleArchived: () -> Unit,
    onToggleGroup: (AccountGroup) -> Unit,
    onSyncMonobank: () -> Unit,
    onRefresh: () -> Unit,
    onMenu: (Account) -> Unit,
) {
    if (state.isEmpty && !state.hasArchived) {
        EmptyState(
            title = stringResource(R.string.feature_accounts_empty_title),
            body = stringResource(R.string.feature_accounts_empty_text),
            action = stringResource(R.string.feature_accounts_add),
            onAction = { actions.openEditor(null) },
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (state.stale) {
            item { StaleBanner(fetchedAt = state.fetchedAt, offline = state.offline, onRetry = onRefresh) }
        }
        state.summary?.let { summary ->
            item { SummaryStrip(summary, state.groups) }
        }
        state.monobankError?.let {
            item {
                WarnCard(
                    text = stringResource(R.string.feature_accounts_monobank_error),
                    action = stringResource(R.string.feature_accounts_sync_now),
                    onAction = onSyncMonobank,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
        state.groups.forEach { row ->
            item(key = "group:${row.group}") {
                GroupHeader(row, state.baseCurrency, onToggle = { onToggleGroup(row.group) })
            }
            if (!row.collapsed) {
                items(row.accounts, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        baseCurrency = state.baseCurrency,
                        difference = state.differences[account.id],
                        onClick = { actions.openTransactions(account.id) },
                        onMenu = { onMenu(account) },
                    )
                }
            }
        }
        if (state.debts.isNotEmpty()) {
            item(
                key = "debts",
            ) { DebtsSection(state.debts, onPerson = { actions.openPayee(it.payeeId, it.name.orEmpty()) }) }
        }
        if (state.hasArchived) {
            item {
                TextButton(onClick = onToggleArchived, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                    Text(
                        stringResource(
                            if (state.showArchived) {
                                R.string.feature_accounts_hide_archived
                            } else {
                                R.string.feature_accounts_show_archived
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStrip(summary: AccountSummary, groups: List<AccountGroupRow>) {
    val currency = summary.currency.code
    val credit = groups.filter { it.group == AccountGroup.CREDIT || it.group == AccountGroup.DEBTS }
        .flatMap { it.accounts }
        .filter { it.type != AccountType.DEBT }
        .mapNotNull { it.balanceInBase }
        .fold(BigDecimal.ZERO) { sum, value -> sum + value }
    WwCard(modifier = Modifier.padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Figure(label = stringResource(R.string.feature_accounts_available), modifier = Modifier.weight(1f)) {
                Amount(summary.available, currency, style = WwTheme.type.amountM)
            }
            Figure(label = stringResource(R.string.feature_accounts_credit_debt), modifier = Modifier.weight(1f)) {
                Amount(credit, currency, style = WwTheme.type.amountM, tone = AmountTone.BySign)
            }
        }
        if (summary.unconvertedCurrencies.isNotEmpty()) {
            Text(
                stringResource(R.string.feature_accounts_without_rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun GroupHeader(row: AccountGroupRow, baseCurrency: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.collapsed) Lucide.ChevronRight else Lucide.ChevronDown,
            contentDescription = stringResource(
                if (row.collapsed) R.string.feature_accounts_expand else R.string.feature_accounts_collapse,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(Spacing.xs))
        Text(groupName(row.group), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        row.totalInBase?.let { total ->
            Amount(
                total,
                baseCurrency,
                style = WwTheme.type.amountS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun groupName(group: AccountGroup): String = stringResource(
    when (group) {
        AccountGroup.CARDS -> R.string.feature_accounts_groups_cards
        AccountGroup.FOP -> R.string.feature_accounts_groups_fop
        AccountGroup.CASH -> R.string.feature_accounts_groups_cash
        AccountGroup.CREDIT -> R.string.feature_accounts_groups_credit
        AccountGroup.SAVINGS -> R.string.feature_accounts_groups_savings
        AccountGroup.DEBTS -> R.string.feature_accounts_groups_debts
        AccountGroup.INVESTMENTS -> R.string.feature_accounts_groups_investments
        AccountGroup.ARCHIVED -> R.string.feature_accounts_groups_archived
    },
)

@Composable
fun typeName(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CARD -> R.string.feature_accounts_type_card
        AccountType.CREDIT_CARD -> R.string.feature_accounts_type_credit_card
        AccountType.CASH -> R.string.feature_accounts_type_cash
        AccountType.SAVINGS -> R.string.feature_accounts_type_savings
        AccountType.DEPOSIT -> R.string.feature_accounts_type_deposit
        AccountType.LOAN -> R.string.feature_accounts_type_loan
        AccountType.DEBT -> R.string.feature_accounts_type_debt
        AccountType.INVESTMENT -> R.string.feature_accounts_type_investment
        AccountType.FOP -> R.string.feature_accounts_type_fop
    },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Account,
    baseCurrency: String,
    difference: BigDecimal?,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AccountTypeIcon(account.type)
        Column(modifier = Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            val limit = account.creditLimit
            val status = when {
                account.archived -> stringResource(R.string.feature_accounts_archived)
                difference != null -> stringResource(
                    R.string.feature_accounts_differs,
                    formattedDifference(difference, account.currency),
                )
                account.type == AccountType.CREDIT_CARD && limit != null && limit.signum() > 0 ->
                    stringResource(
                        R.string.feature_accounts_limit,
                        formatters.money.format(limit, account.currency.code),
                    )
                else -> typeName(account.type)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (difference != null) WwTheme.colors.warnText else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(account.balance, account.currency.code, tone = AmountTone.BySign)
            val inBase = account.balanceInBase
            if (inBase != null && account.currency.code != baseCurrency) {
                Amount(
                    inBase,
                    baseCurrency,
                    style = WwTheme.type.amountS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMenu) {
            Icon(Lucide.EllipsisVertical, contentDescription = stringResource(R.string.feature_accounts_actions))
        }
    }
}

@Composable
private fun formattedDifference(value: BigDecimal, currency: CurrencyCode): String =
    formatters.money.format(value, currency.code, signed = true)

/** The people of `GET /debts` under the loans group (docs/04 Debts); settled ones behind a toggle, as on the web. */
@Composable
private fun DebtsSection(people: List<DebtPerson>, onPerson: (DebtPerson) -> Unit) {
    var showSettled by rememberSaveable { mutableStateOf(false) }
    val open = people.filter { it.balance.signum() != 0 }
    val settled = people.filter { it.balance.signum() == 0 }
    Column {
        Text(
            stringResource(R.string.feature_accounts_debts_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        (open + if (showSettled) settled else emptyList()).forEach { person ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPerson(person) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        person.name ?: stringResource(R.string.feature_accounts_debts_no_name),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when {
                            person.balance.signum() > 0 -> stringResource(R.string.feature_accounts_debts_owes_you)
                            person.balance.signum() < 0 -> stringResource(R.string.feature_accounts_debts_you_owe)
                            else -> stringResource(R.string.feature_accounts_debts_settled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Amount(
                    person.balance,
                    person.currency.code,
                    color = if (person.balance.signum() < 0) {
                        WwTheme.colors.expense
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (settled.isNotEmpty()) {
            TextButton(onClick = { showSettled = !showSettled }, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                Text(
                    if (showSettled) {
                        stringResource(R.string.feature_accounts_debts_hide_settled)
                    } else {
                        stringResource(R.string.feature_accounts_debts_show_settled, settled.size)
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Suppress("MagicNumber")
internal fun previewAccount(
    id: Long,
    name: String,
    type: AccountType,
    balance: String,
    currency: String = "UAH",
) = Account(
    id = id,
    name = name,
    type = type,
    currency = CurrencyCode(currency),
    balance = BigDecimal(balance),
    balanceInBase = BigDecimal(balance),
    openingBalance = BigDecimal.ZERO,
    creditLimit = null,
    availableCredit = null,
    interestRate = null,
    inBudget = true,
    archived = false,
    sortOrder = 0,
    operations = 0,
)

@Suppress("MagicNumber")
@PreviewLightDark
@Composable
private fun AccountsPreview() {
    val accounts = listOf(
        previewAccount(1, "Чорна картка", AccountType.CARD, "12450.20"),
        previewAccount(2, "Готівка", AccountType.CASH, "800"),
        previewAccount(3, "Кредитка", AccountType.CREDIT_CARD, "-3200"),
    )
    WebWalletTheme {
        AccountsScreen(
            state = AccountsUiState.Content(
                summary = AccountSummary(
                    CurrencyCode.UAH,
                    BigDecimal("10050.20"),
                    BigDecimal("13250.20"),
                    BigDecimal("20000"),
                    BigDecimal("1500"),
                    emptyList(),
                ),
                baseCurrency = "UAH",
                groups = AccountsViewModel.groupAccounts(accounts, false, emptySet()),
                debts = listOf(DebtPerson(7, "Олена", BigDecimal("500"), CurrencyCode.UAH, 2, null)),
                showArchived = false,
                hasArchived = true,
                differences = mapOf(1L to BigDecimal("-120")),
                monobankError = null,
                stale = true,
                fetchedAt = null,
                offline = true,
                refreshing = false,
            ),
            snackbar = SnackbarHostState(),
            actions = AccountsActions({}, {}, { _, _ -> }, { _, _ -> }),
            onRefresh = {},
            onToggleArchived = {},
            onToggleGroup = {},
            onSetArchived = { _, _ -> },
            onSyncMonobank = {},
        )
    }
}
