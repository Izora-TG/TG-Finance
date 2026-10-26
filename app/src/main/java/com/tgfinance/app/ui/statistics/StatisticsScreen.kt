package com.tgfinance.app.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountText
import com.tgfinance.app.ui.components.LabeledAmount
import com.tgfinance.app.ui.components.SectionCard
import com.tgfinance.app.ui.components.charts.BarGroup
import com.tgfinance.app.ui.components.charts.ChartSlice
import com.tgfinance.app.ui.components.charts.DonutChart
import com.tgfinance.app.ui.components.charts.IncomeExpenseBarChart
import com.tgfinance.app.ui.components.charts.TrendLineChart
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.DateFormats
import com.tgfinance.app.util.periodLabel
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodType
import java.time.LocalDate

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(
        state = state,
        onPeriodTypeChange = viewModel::setPeriodType,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onCategoryTypeChange = viewModel::setCategoryType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onPeriodTypeChange: (PeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCategoryTypeChange: (TransactionType) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_statistics)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 360.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalItemSpacing = Spacing.l,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                PeriodSelector(state, onPeriodTypeChange, onPrevious, onNext)
            }
            item(span = StaggeredGridItemSpan.FullLine) { SummaryCard(state) }
            item { CategoryChartCard(state, onCategoryTypeChange) }
            item { BarChartCard(state) }
            item { TrendCard(state) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    state: StatisticsUiState,
    onPeriodTypeChange: (PeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val types = PeriodType.entries
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.selection.periodType == type,
                    onClick = { onPeriodTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                ) {
                    Text(
                        stringResource(
                            when (type) {
                                PeriodType.WEEK -> R.string.period_week
                                PeriodType.MONTH -> R.string.period_month
                                PeriodType.QUARTER -> R.string.period_quarter
                                PeriodType.YEAR -> R.string.period_year
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.action_previous_period))
            }
            Text(
                state.range?.let { periodLabel(state.selection.periodType, it) }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = state.selection.offset > 0) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.action_next_period))
            }
        }
    }
}

@Composable
private fun SummaryCard(state: StatisticsUiState) {
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            LabeledAmount(stringResource(R.string.label_income), state.totals?.income ?: 0, Modifier.weight(1f), TransactionType.INCOME)
            LabeledAmount(stringResource(R.string.label_expenses), state.totals?.expense ?: 0, Modifier.weight(1f), TransactionType.EXPENSE)
            LabeledAmount(stringResource(R.string.label_net), state.totals?.net ?: 0, Modifier.weight(1f), alignEnd = true)
        }
        state.savingsRate?.let { rate ->
            Spacer(Modifier.height(Spacing.s))
            Text(
                stringResource(R.string.statistics_savings_rate, (rate * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = if (rate >= 0) MaterialTheme.finance.income else MaterialTheme.finance.expense,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChartCard(state: StatisticsUiState, onCategoryTypeChange: (TransactionType) -> Unit) {
    val formatter = LocalMoneyFormatter.current
    var showAll by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.statistics_by_category)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilterChip(
                selected = state.selection.categoryType == TransactionType.EXPENSE,
                onClick = { onCategoryTypeChange(TransactionType.EXPENSE) },
                label = { Text(stringResource(R.string.label_expenses)) },
            )
            FilterChip(
                selected = state.selection.categoryType == TransactionType.INCOME,
                onClick = { onCategoryTypeChange(TransactionType.INCOME) },
                label = { Text(stringResource(R.string.label_income)) },
            )
        }
        Spacer(Modifier.height(Spacing.m))
        if (state.categoryTotals.isEmpty()) {
            Text(
                stringResource(R.string.statistics_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val description = state.categoryTotals.joinToString { "${it.category.name} ${formatter.format(it.total)}" }
        DonutChart(
            slices = state.categoryTotals.map { ChartSlice(it.total.toFloat(), Color(it.category.color)) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            description = description,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.label_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(state.categorySum, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(Spacing.l))
        val visible = if (showAll) state.categoryTotals else state.categoryTotals.take(5)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            visible.forEach { item ->
                val share = if (state.categorySum > 0) item.total * 100f / state.categorySum else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(item.category.color)),
                    )
                    Text(
                        item.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.s),
                    )
                    Text(
                        "%.1f %%".format(share),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.m),
                    )
                    Text(formatter.format(item.total), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (state.categoryTotals.size > 5) {
            androidx.compose.material3.TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.action_show_less else R.string.action_show_all))
            }
        }
    }
}

@Composable
private fun BarChartCard(state: StatisticsUiState) {
    val formatter = LocalMoneyFormatter.current
    var selected by rememberSaveable(state.range) { mutableStateOf<Int?>(null) }
    val groups = state.buckets.map {
        BarGroup(
            label = if (state.bucketsAreDays) it.start.format(DateFormats.dayOfMonth) else it.start.format(DateFormats.shortMonth),
            income = it.income,
            expense = it.expense,
        )
    }
    SectionCard(title = stringResource(R.string.statistics_income_vs_expense)) {
        val current = selected?.let { state.buckets.getOrNull(it) }
        Text(
            text = if (current != null) {
                val label = if (state.bucketsAreDays) current.start.format(DateFormats.medium) else current.start.format(DateFormats.monthYear)
                "$label: +${formatter.format(current.income)} / −${formatter.format(current.expense)}"
            } else {
                stringResource(R.string.statistics_tap_bar)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))
        IncomeExpenseBarChart(
            groups = groups,
            selectedIndex = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.statistics_income_vs_expense),
        )
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Legend(MaterialTheme.finance.income, stringResource(R.string.label_income))
            Legend(MaterialTheme.finance.expense, stringResource(R.string.label_expenses))
        }
    }
}

@Composable
private fun TrendCard(state: StatisticsUiState) {
    SectionCard(title = stringResource(R.string.statistics_balance_trend)) {
        val last = state.balanceTrend.lastOrNull()
        if (last != null) {
            AmountText(last.balance, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.statistics_balance_at, last.date.format(DateFormats.medium)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.m))
        }
        TrendLineChart(
            values = state.balanceTrend.map { it.balance },
            description = stringResource(R.string.statistics_balance_trend),
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.xs))
    }
}

private val previewState = StatisticsUiState(
    loading = false,
    range = DateRange(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31)),
    totals = PreviewData.monthTotals,
    categoryTotals = PreviewData.categoryTotals,
    buckets = PreviewData.barTotals,
    bucketsAreDays = false,
    balanceTrend = PreviewData.balanceTrend,
)

@Preview(device = Devices.PIXEL_7, heightDp = 1600)
@Composable
private fun StatisticsPreview() {
    TgFinanceTheme { StatisticsScreen(previewState, {}, {}, {}, {}) }
}

@Preview(device = Devices.PIXEL_TABLET)
@Composable
private fun StatisticsTabletPreview() {
    TgFinanceTheme { StatisticsScreen(previewState, {}, {}, {}, {}) }
}
