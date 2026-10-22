package org.andrewdev.webwallet.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.designsystem.R
import org.andrewdev.webwallet.core.designsystem.icons.AccountTypeIcons
import org.andrewdev.webwallet.core.designsystem.icons.CategoryIcons
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme

/**
 * Shows every token of the design system (docs/05) so that the theme can be checked on a device in both modes.
 * Reachable from the debug Developer screen once it exists; in M0 it is what the app opens on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.core_designsystem_gallery_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item { ColorsSection() }
            item { TypographySection() }
            item { PaletteSection() }
            item { ComponentsSection() }
            item { IconsSection() }
        }
    }
}

private const val SWATCH_COLUMNS = 4
private const val ICON_COLUMNS = 8

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.sm))
}

@Composable
private fun ColorsSection() {
    val scheme = MaterialTheme.colorScheme
    val ww = WwTheme.colors
    val swatches = listOf(
        "primary" to scheme.primary,
        "primaryContainer" to scheme.primaryContainer,
        "secondaryContainer" to scheme.secondaryContainer,
        "tertiary" to scheme.tertiary,
        "error" to scheme.error,
        "errorContainer" to scheme.errorContainer,
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "outline" to scheme.outline,
        "income" to ww.income,
        "expense" to ww.expense,
        "transfer" to ww.transfer,
        "warnBg" to ww.warnBg,
        "warnBorder" to ww.warnBorder,
        "faint" to ww.faint,
    )
    Column {
        SectionTitle(stringResource(R.string.core_designsystem_gallery_colors))
        SwatchGrid(swatches)
    }
}

@Composable
private fun SwatchGrid(swatches: List<Pair<String, Color>>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(SWATCH_COLUMNS),
        modifier = Modifier.heightIn(max = 600.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        userScrollEnabled = false,
    ) {
        items(swatches) { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(color, MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                )
                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TypographySection() {
    val type = MaterialTheme.typography
    val samples: List<Pair<String, TextStyle>> = listOf(
        "displaySmall" to type.displaySmall,
        "headlineSmall" to type.headlineSmall,
        "titleLarge" to type.titleLarge,
        "titleMedium" to type.titleMedium,
        "titleSmall" to type.titleSmall,
        "bodyLarge" to type.bodyLarge,
        "bodyMedium" to type.bodyMedium,
        "bodySmall" to type.bodySmall,
        "labelLarge" to type.labelLarge,
        "labelMedium" to type.labelMedium,
        "labelSmall" to type.labelSmall,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(stringResource(R.string.core_designsystem_gallery_typography))
        samples.forEach { (name, style) -> Text(text = name, style = style) }
        Text(text = "-1 234,50 ₴", style = WwTheme.type.amountL, color = WwTheme.colors.expense)
        Text(text = "+12 000,00 ₴", style = WwTheme.type.amountM, color = WwTheme.colors.income)
        Text(text = "1 234,50 ₴", style = WwTheme.type.amountS, color = WwTheme.colors.transfer)
        Text(text = "ABCD-EFGH-1234", style = WwTheme.type.mono)
    }
}

@Composable
private fun PaletteSection() {
    val dark = isSystemInDarkTheme()
    Column {
        SectionTitle(stringResource(R.string.core_designsystem_gallery_palette))
        SwatchGrid(PaletteColor.entries.map { it.key to it.color(dark) })
    }
}

@Composable
private fun ComponentsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.core_designsystem_gallery_components))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = {}) { Text(stringResource(R.string.core_designsystem_gallery_button)) }
            OutlinedButton(onClick = {}) { Text(stringResource(R.string.core_designsystem_gallery_button)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(R.string.core_designsystem_gallery_chip)) },
            )
            FilterChip(
                selected = false,
                onClick = {},
                label = { Text(stringResource(R.string.core_designsystem_gallery_chip)) },
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = CardDefaults.outlinedCardBorder(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.core_designsystem_gallery_card_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.core_designsystem_gallery_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun IconsSection() {
    val dark = isSystemInDarkTheme()
    val accountIcons = listOf(
        AccountTypeIcons.card,
        AccountTypeIcons.cash,
        AccountTypeIcons.savings,
        AccountTypeIcons.deposit,
        AccountTypeIcons.loan,
        AccountTypeIcons.debt,
        AccountTypeIcons.investment,
        AccountTypeIcons.fop,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(stringResource(R.string.core_designsystem_gallery_icons))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            accountIcons.forEach { icon -> Icon(imageVector = icon, contentDescription = null) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(ICON_COLUMNS),
            modifier = Modifier.heightIn(max = 400.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            userScrollEnabled = false,
        ) {
            items(CategoryIcons.byName.entries.toList()) { (name, icon) ->
                val color = PaletteColor.auto(name.hashCode().toLong())
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.container(dark), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = color.color(dark),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun GalleryScreenPreview() {
    WebWalletTheme {
        GalleryScreen()
    }
}
