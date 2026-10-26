package org.andrewdev.webwallet.feature.transactions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.data.TransferPair
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.transactions.FeedUiState
import org.andrewdev.webwallet.feature.transactions.FeedViewModel
import org.andrewdev.webwallet.feature.transactions.R

/**
 * The suggested transfer pairs of the period (docs/04 §6.5): both rows and the difference, Merge or Not a transfer
 * per pair. Reuses the feed ViewModel, whose pairs follow the chosen period.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferPairsRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: FeedViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val report = rememberErrorReporter(snackbar)
    val f = formatters
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_transactions_transfer_pairs)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PeriodChips(filters.preset, onPick = { viewModel.setPreset(it) })
            val content = state as? FeedUiState.Content
            if (content == null) {
                SkeletonList()
                return@Column
            }
            val pairs = content.rows.values.mapNotNull { it.pair }.distinct()
            if (pairs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.feature_transactions_pairs_empty),
                    body = stringResource(R.string.feature_transactions_pairs_hint),
                )
                return@Column
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(pairs, key = { "${it.expenseId}-${it.incomeId}" }) { pair: TransferPair ->
                    WwCard {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                f.date.short(pair.date, f.today),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            content.rows[pair.expenseId]?.let {
                                TransactionRow(
                                    it,
                                    onClick = { onOpen(pair.expenseId) },
                                    onCategorize = {},
                                )
                            }
                            content.rows[pair.incomeId]?.let {
                                TransactionRow(
                                    it,
                                    onClick = { onOpen(pair.incomeId) },
                                    onCategorize = {},
                                )
                            }
                            val currency = content.rows[pair.expenseId]?.transaction?.currency?.code ?: "UAH"
                            Text(
                                when {
                                    pair.exact -> stringResource(R.string.feature_transactions_pair_exact)
                                    pair.difference.signum() < 0 -> stringResource(
                                        R.string.feature_transactions_pair_less,
                                        f.money.format(pair.difference.abs(), currency),
                                    )
                                    else -> stringResource(
                                        R.string.feature_transactions_pair_more,
                                        f.money.format(pair.difference, currency),
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    viewModel.dismissPair(pair, report)
                                }) { Text(stringResource(R.string.feature_transactions_pair_dismiss)) }
                                TextButton(onClick = {
                                    viewModel.mergePair(pair, report)
                                }) { Text(stringResource(R.string.feature_transactions_pair_merge)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
