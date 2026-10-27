package org.andrewdev.webwallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.BuildConfig
import org.andrewdev.webwallet.R
import org.andrewdev.webwallet.core.designsystem.icons.TabIcons
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.navigation.AboutKey
import org.andrewdev.webwallet.core.navigation.AccountEditorKey
import org.andrewdev.webwallet.core.navigation.AccountTransactionsKey
import org.andrewdev.webwallet.core.navigation.AccountsKey
import org.andrewdev.webwallet.core.navigation.AppLockSettingsKey
import org.andrewdev.webwallet.core.navigation.BudgetKey
import org.andrewdev.webwallet.core.navigation.CategoriesSettingsKey
import org.andrewdev.webwallet.core.navigation.CategoryEditorKey
import org.andrewdev.webwallet.core.navigation.CategoryTransactionsKey
import org.andrewdev.webwallet.core.navigation.ClaimDemoKey
import org.andrewdev.webwallet.core.navigation.ComingSoonKey
import org.andrewdev.webwallet.core.navigation.DataSettingsKey
import org.andrewdev.webwallet.core.navigation.DeepLink
import org.andrewdev.webwallet.core.navigation.GoalEditorKey
import org.andrewdev.webwallet.core.navigation.GoalsKey
import org.andrewdev.webwallet.core.navigation.MoreKey
import org.andrewdev.webwallet.core.navigation.NotificationsSettingsKey
import org.andrewdev.webwallet.core.navigation.OverviewKey
import org.andrewdev.webwallet.core.navigation.PayeeTransactionsKey
import org.andrewdev.webwallet.core.navigation.PayeesSettingsKey
import org.andrewdev.webwallet.core.navigation.PinSetupKey
import org.andrewdev.webwallet.core.navigation.PinSetupMode
import org.andrewdev.webwallet.core.navigation.PrivacySettingsKey
import org.andrewdev.webwallet.core.navigation.ProfileSettingsKey
import org.andrewdev.webwallet.core.navigation.RecurringEditorKey
import org.andrewdev.webwallet.core.navigation.RecurringListKey
import org.andrewdev.webwallet.core.navigation.ReportsKey
import org.andrewdev.webwallet.core.navigation.RuleEditorKey
import org.andrewdev.webwallet.core.navigation.RulesSettingsKey
import org.andrewdev.webwallet.core.navigation.SecuritySettingsKey
import org.andrewdev.webwallet.core.navigation.SettingsKey
import org.andrewdev.webwallet.core.navigation.TagsSettingsKey
import org.andrewdev.webwallet.core.navigation.TransactionEditorKey
import org.andrewdev.webwallet.core.navigation.TransactionsKey
import org.andrewdev.webwallet.core.navigation.TransferPairsKey
import org.andrewdev.webwallet.feature.accounts.ui.AccountEditorRoute
import org.andrewdev.webwallet.feature.accounts.ui.AccountsActions
import org.andrewdev.webwallet.feature.accounts.ui.AccountsRoute
import org.andrewdev.webwallet.feature.budget.ui.BudgetActions
import org.andrewdev.webwallet.feature.budget.ui.BudgetRoute
import org.andrewdev.webwallet.feature.goals.GoalsActions
import org.andrewdev.webwallet.feature.goals.ui.GoalEditorRoute
import org.andrewdev.webwallet.feature.goals.ui.GoalsRoute
import org.andrewdev.webwallet.feature.lock.ui.PinSetupRoute
import org.andrewdev.webwallet.feature.lock.ui.biometricAvailable
import org.andrewdev.webwallet.feature.more.ClaimDemoRoute
import org.andrewdev.webwallet.feature.more.ComingSoonScreen
import org.andrewdev.webwallet.feature.more.DemoBanner
import org.andrewdev.webwallet.feature.more.MoreActions
import org.andrewdev.webwallet.feature.more.MoreRoute
import org.andrewdev.webwallet.feature.more.MoreSection
import org.andrewdev.webwallet.feature.overview.ui.OverviewActions
import org.andrewdev.webwallet.feature.overview.ui.OverviewRoute
import org.andrewdev.webwallet.feature.reports.ReportsActions
import org.andrewdev.webwallet.feature.reports.ui.ReportsRoute
import org.andrewdev.webwallet.feature.settings.CategoriesActions
import org.andrewdev.webwallet.feature.settings.ui.AboutScreen
import org.andrewdev.webwallet.feature.settings.ui.AppLockSettingsRoute
import org.andrewdev.webwallet.feature.settings.ui.CategoriesRoute
import org.andrewdev.webwallet.feature.settings.ui.CategoryEditorRoute
import org.andrewdev.webwallet.feature.settings.ui.DataRoute
import org.andrewdev.webwallet.feature.settings.ui.NotificationsRoute
import org.andrewdev.webwallet.feature.settings.ui.PayeesRoute
import org.andrewdev.webwallet.feature.settings.ui.PrivacyRoute
import org.andrewdev.webwallet.feature.settings.ui.ProfileRoute
import org.andrewdev.webwallet.feature.settings.ui.RuleEditorRoute
import org.andrewdev.webwallet.feature.settings.ui.RulesRoute
import org.andrewdev.webwallet.feature.settings.ui.SecurityRoute
import org.andrewdev.webwallet.feature.settings.ui.SettingsActions
import org.andrewdev.webwallet.feature.settings.ui.SettingsRootScreen
import org.andrewdev.webwallet.feature.settings.ui.TagsRoute
import org.andrewdev.webwallet.feature.transactions.ui.EditorArgs
import org.andrewdev.webwallet.feature.transactions.ui.EditorRoute
import org.andrewdev.webwallet.feature.transactions.ui.FeedActions
import org.andrewdev.webwallet.feature.transactions.ui.FeedRoute
import org.andrewdev.webwallet.feature.transactions.ui.FeedScope
import org.andrewdev.webwallet.feature.transactions.ui.RecurringEditorRoute
import org.andrewdev.webwallet.feature.transactions.ui.RecurringListRoute
import org.andrewdev.webwallet.feature.transactions.ui.TransferPairsRoute

/** The five tabs (docs/02 §7), each with its own back stack. */
private val TABS: List<NavKey> = listOf(OverviewKey, TransactionsKey, BudgetKey, AccountsKey, MoreKey)

private const val STORE_URL = "https://play.google.com/store/apps/details?id=org.andrewdev.webwallet"

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun MainScaffold(deepLink: DeepLink.Main?, onDeepLinkConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stacks = TABS.map { rememberNavBackStack(it) }
    val stack = stacks[tab]
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(deepLink) {
        if (deepLink == null) return@LaunchedEffect
        tab = TABS.indexOf(deepLink.tab).coerceAtLeast(0)
        deepLink.key?.let { stacks[tab].add(it) }
        onDeepLinkConsumed()
    }

    // The system back from a tab root goes to the overview, then exits
    BackHandler(enabled = stack.size == 1 && tab != 0) { tab = 0 }

    val savedText = stringResource(R.string.app_saved)
    val addAnotherText = stringResource(R.string.app_add_another)
    val openEditor: (TransactionEditorKey) -> Unit = { stack.add(it) }
    val fabVisible = stack.size == 1 && TABS[tab] in setOf(OverviewKey, TransactionsKey, AccountsKey)

    Scaffold(
        bottomBar = {
            if (stack.size == 1) {
                NavigationBar {
                    TABS.forEachIndexed { index, key ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = {
                                if (tab == index) stacks[index].popToRoot() else tab = index
                            },
                            icon = { Icon(tabIcon(key), contentDescription = null) },
                            label = { Text(tabName(key)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (fabVisible) {
                FloatingActionButton(
                    onClick = { openEditor(TransactionEditorKey(type = TransactionType.EXPENSE, fromFab = true)) },
                ) {
                    Icon(Lucide.Plus, contentDescription = stringResource(R.string.app_add_transaction))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            if (stack.size == 1) DemoBanner(onKeep = { stack.add(ClaimDemoKey) })
            NavDisplay(
                backStack = stack,
                modifier = Modifier.weight(1f),
                onBack = { stack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<OverviewKey> {
                        OverviewRoute(
                            OverviewActions(
                                openTransactions = { tab = 1 },
                                openTransaction = { stack.add(TransactionEditorKey(id = it)) },
                                openBudget = { tab = 2 },
                                openAccounts = { tab = 3 },
                                newAccount = { stack.add(AccountEditorKey()) },
                                openCategory = { id, name, month ->
                                    stack.add(CategoryTransactionsKey(id, name, month.toString()))
                                },
                            ),
                        )
                    }
                    entry<TransactionsKey> {
                        FeedRoute(FeedScope.All, feedActions(stack, openEditor))
                    }
                    entry<AccountTransactionsKey> { key ->
                        FeedRoute(
                            FeedScope.Account(key.accountId),
                            feedActions(stack, openEditor),
                        )
                    }
                    entry<PayeeTransactionsKey> { key ->
                        FeedRoute(
                            FeedScope.Payee(key.payeeId, key.name),
                            feedActions(stack, openEditor),
                        )
                    }
                    entry<RecurringListKey> {
                        RecurringListRoute(
                            onOpen = { stack.add(RecurringEditorKey(it)) },
                            onBack = { stack.removeLastOrNull() },
                        )
                    }
                    entry<RecurringEditorKey> { key ->
                        RecurringEditorRoute(id = key.id, onClose = { stack.removeLastOrNull() })
                    }
                    entry<TransferPairsKey> {
                        TransferPairsRoute(
                            onBack = { stack.removeLastOrNull() },
                            onOpen = { stack.add(TransactionEditorKey(id = it)) },
                        )
                    }
                    entry<TransactionEditorKey> { key ->
                        EditorRoute(
                            args = EditorArgs(
                                key.id,
                                key.type,
                                key.accountId,
                                key.categoryId,
                                key.payeeId,
                                key.fromFab,
                            ),
                            onClose = { stack.removeLastOrNull() },
                            onSaved = { _, fromFab ->
                                stack.removeLastOrNull()
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        savedText,
                                        actionLabel = if (fromFab) addAnotherText else null,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        openEditor(TransactionEditorKey(type = TransactionType.EXPENSE, fromFab = true))
                                    }
                                }
                            },
                        )
                    }
                    entry<BudgetKey> {
                        BudgetRoute(
                            BudgetActions(
                                openCategoryTransactions = { id, name, month ->
                                    stack.add(CategoryTransactionsKey(id, name, month.toString()))
                                },
                            ),
                        )
                    }
                    entry<CategoryTransactionsKey> { key ->
                        FeedRoute(
                            FeedScope.Category(key.categoryId, key.name, key.month),
                            feedActions(stack, openEditor),
                        )
                    }
                    entry<AccountsKey> {
                        AccountsRoute(
                            AccountsActions(
                                openEditor = { stack.add(AccountEditorKey(it)) },
                                openTransactions = { stack.add(AccountTransactionsKey(it)) },
                                openPayee = { id, name -> stack.add(PayeeTransactionsKey(id, name)) },
                                newTransaction = { accountId, type ->
                                    openEditor(
                                        TransactionEditorKey(type = type, accountId = accountId),
                                    )
                                },
                            ),
                        )
                    }
                    entry<AccountEditorKey> { key ->
                        AccountEditorRoute(
                            id = key.id,
                            onClose = { stack.removeLastOrNull() },
                        )
                    }
                    entry<MoreKey> {
                        MoreRoute(
                            MoreActions(
                                openSettings = { stack.add(SettingsKey) },
                                openAbout = { stack.add(AboutKey) },
                                openSection = { section ->
                                    when (section) {
                                        MoreSection.GOALS -> stack.add(GoalsKey)
                                        MoreSection.REPORTS -> stack.add(ReportsKey)
                                        else -> stack.add(ComingSoonKey(section.name.lowercase()))
                                    }
                                },
                            ),
                        )
                    }
                    entry<ReportsKey> {
                        ReportsRoute(
                            ReportsActions(
                                openCategory = { id, name, month ->
                                    stack.add(CategoryTransactionsKey(id, name, month.toString()))
                                },
                                openPayee = { id, name -> stack.add(PayeeTransactionsKey(id, name)) },
                                back = { stack.removeLastOrNull() },
                            ),
                        )
                    }
                    entry<GoalsKey> {
                        GoalsRoute(
                            GoalsActions(
                                openEditor = { stack.add(GoalEditorKey(it)) },
                                back = { stack.removeLastOrNull() },
                            ),
                        )
                    }
                    entry<GoalEditorKey> { key ->
                        GoalEditorRoute(id = key.id, onClose = { stack.removeLastOrNull() })
                    }
                    entry<ComingSoonKey> { key ->
                        ComingSoonScreen(
                            section = key.section,
                            onBack = { stack.removeLastOrNull() },
                        )
                    }
                    entry<ClaimDemoKey> { ClaimDemoRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<SettingsKey> {
                        SettingsRootScreen(
                            SettingsActions(
                                back = { stack.removeLastOrNull() },
                                openProfile = { stack.add(ProfileSettingsKey) },
                                openSecurity = { stack.add(SecuritySettingsKey) },
                                openAppLock = { stack.add(AppLockSettingsKey) },
                                openPrivacy = { stack.add(PrivacySettingsKey) },
                                openAbout = { stack.add(AboutKey) },
                                openNotifications = { stack.add(NotificationsSettingsKey) },
                                openCategories = { stack.add(CategoriesSettingsKey) },
                                openRules = { stack.add(RulesSettingsKey) },
                                openPayees = { stack.add(PayeesSettingsKey) },
                                openTags = { stack.add(TagsSettingsKey) },
                                openData = { stack.add(DataSettingsKey) },
                            ),
                        )
                    }
                    entry<NotificationsSettingsKey> { NotificationsRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<CategoriesSettingsKey> {
                        CategoriesRoute(
                            CategoriesActions(
                                openEditor = { id, kind, parentId ->
                                    stack.add(CategoryEditorKey(id, kind.name, parentId))
                                },
                                back = { stack.removeLastOrNull() },
                            ),
                        )
                    }
                    entry<CategoryEditorKey> { key ->
                        CategoryEditorRoute(
                            id = key.id,
                            kind = CategoryKind.valueOf(key.kind),
                            parentId = key.parentId,
                            onClose = { stack.removeLastOrNull() },
                        )
                    }
                    entry<RulesSettingsKey> {
                        RulesRoute(
                            openEditor = { stack.add(RuleEditorKey(it)) },
                            onBack = { stack.removeLastOrNull() },
                        )
                    }
                    entry<RuleEditorKey> { key ->
                        RuleEditorRoute(id = key.id, onClose = { stack.removeLastOrNull() })
                    }
                    entry<PayeesSettingsKey> { PayeesRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<TagsSettingsKey> { TagsRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<DataSettingsKey> { DataRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<ProfileSettingsKey> { ProfileRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<SecuritySettingsKey> {
                        SecurityRoute(
                            onBack = { stack.removeLastOrNull() },
                            onAppLock = { stack.add(AppLockSettingsKey) },
                        )
                    }
                    entry<AppLockSettingsKey> {
                        AppLockSettingsRoute(
                            biometricAvailable = biometricAvailable(context),
                            onBack = { stack.removeLastOrNull() },
                            onSetUp = { stack.add(PinSetupKey(PinSetupMode.CREATE)) },
                            onChangePin = { stack.add(PinSetupKey(PinSetupMode.CHANGE)) },
                        )
                    }
                    entry<PinSetupKey> { key ->
                        PinSetupRoute(change = key.mode == PinSetupMode.CHANGE, onClose = {
                            stack.removeLastOrNull()
                        })
                    }
                    entry<PrivacySettingsKey> { PrivacyRoute(onBack = { stack.removeLastOrNull() }) }
                    entry<AboutKey> {
                        AboutScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            backendUrl = if (BuildConfig.DEBUG) BuildConfig.API_BASE_URL else null,
                            storeUrl = STORE_URL,
                            onBack = { stack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}

private fun feedActions(stack: NavBackStack<NavKey>, openEditor: (TransactionEditorKey) -> Unit) = FeedActions(
    openEditor = { openEditor(TransactionEditorKey(id = it)) },
    newTransaction = { openEditor(TransactionEditorKey(type = TransactionType.EXPENSE)) },
    openTransferPairs = { stack.add(TransferPairsKey) },
    openRecurring = { stack.add(RecurringListKey) },
    editRecurring = { stack.add(RecurringEditorKey(it)) },
    back = { stack.removeLastOrNull() },
)

private fun NavBackStack<NavKey>.popToRoot() {
    while (size > 1) removeLastOrNull()
}

private fun tabIcon(key: NavKey) = when (key) {
    OverviewKey -> TabIcons.overview
    TransactionsKey -> TabIcons.transactions
    BudgetKey -> TabIcons.budget
    AccountsKey -> TabIcons.accounts
    else -> TabIcons.more
}

@Composable
private fun tabName(key: NavKey): String = stringResource(
    when (key) {
        OverviewKey -> R.string.app_tab_overview
        TransactionsKey -> R.string.app_tab_transactions
        BudgetKey -> R.string.app_tab_budget
        AccountsKey -> R.string.app_tab_accounts
        else -> R.string.app_tab_more
    },
)
