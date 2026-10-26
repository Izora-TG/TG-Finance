package com.tgfinance.app.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance

data class ChartSlice(
    val value: Float,
    val color: Color,
)

/** Donut chart; the centre is free for a label (e.g. the total). */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    description: String = "",
    center: @Composable BoxScope.() -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 2f else 0f
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.value / total * progress.value
                if (sweep > gap) {
                    drawArc(slice.color, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        center()
    }
}

data class BarGroup(
    val label: String,
    val income: Long,
    val expense: Long,
)

/**
 * Income/expense bars per bucket. Tapping a bar reports its index so the caller can show details.
 */
@Composable
fun IncomeExpenseBarChart(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    selectedIndex: Int? = null,
    description: String = "",
    onSelect: (Int) -> Unit = {},
) {
    val income = MaterialTheme.finance.income
    val expense = MaterialTheme.finance.expense
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val measurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(groups) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600))
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(groups) {
                detectTapGestures { offset ->
                    if (groups.isNotEmpty()) {
                        val slot = size.width / groups.size.toFloat()
                        onSelect((offset.x / slot).toInt().coerceIn(0, groups.lastIndex))
                    }
                }
            },
    ) {
        if (groups.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val max = groups.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1L).toFloat()
        // three horizontal grid lines
        for (i in 1..3) {
            val y = chartHeight * (1f - i / 3f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        drawLine(grid, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())

        val slot = size.width / groups.size
        val barWidth = (slot * 0.32f).coerceAtMost(18.dp.toPx())
        val radius = CornerRadius(barWidth / 2.5f)
        val labelEvery = when {
            groups.size > 20 -> 5
            groups.size > 12 -> 2
            else -> 1
        }
        groups.forEachIndexed { index, group ->
            val centerX = slot * index + slot / 2
            if (index == selectedIndex) {
                drawRoundRect(highlight, Offset(slot * index, 0f), Size(slot, chartHeight), CornerRadius(8.dp.toPx()))
            }
            val incomeHeight = chartHeight * group.income / max * progress.value
            val expenseHeight = chartHeight * group.expense / max * progress.value
            drawRoundRect(income, Offset(centerX - barWidth - 1.dp.toPx(), chartHeight - incomeHeight), Size(barWidth, incomeHeight), radius)
            drawRoundRect(expense, Offset(centerX + 1.dp.toPx(), chartHeight - expenseHeight), Size(barWidth, expenseHeight), radius)
            if (index % labelEvery == 0) {
                val layout = measurer.measure(group.label, TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, chartHeight + 4.dp.toPx()))
            }
        }
    }
}

/** Smooth line chart with a soft gradient fill, used for the balance trend. */
@Composable
fun TrendLineChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    description: String = "",
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zeroLine = MaterialTheme.finance.expense.copy(alpha = 0.5f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        if (values.size < 2) return@Canvas
        val min = minOf(values.min(), 0L).toFloat()
        val max = values.max().toFloat().let { if (it == min) it + 1f else it }
        val range = max - min
        val stepX = size.width / (values.size - 1)
        fun y(v: Long) = size.height - (v - min) / range * size.height * 0.9f - size.height * 0.05f

        for (i in 0..3) {
            val gy = size.height * i / 3f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        if (min < 0f) {
            val zeroY = y(0)
            drawLine(zeroLine, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
        }

        val line = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val yy = y(value)
            if (index == 0) {
                line.moveTo(x, yy)
            } else {
                val prevX = stepX * (index - 1)
                val prevY = y(values[index - 1])
                val midX = (prevX + x) / 2
                line.cubicTo(midX, prevY, midX, yy, x, yy)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val lastX = stepX * values.lastIndex
        drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(lastX, y(values.last())))
    }
}

@Preview
@Composable
private fun ChartsPreview() {
    TgFinanceTheme {
        Column(Modifier.padding(Spacing.l)) {
            DonutChart(
                slices = listOf(
                    ChartSlice(50f, Color(0xFF8B5CF6)),
                    ChartSlice(30f, Color(0xFF22C55E)),
                    ChartSlice(20f, Color(0xFFF97316)),
                ),
            )
            IncomeExpenseBarChart(
                groups = listOf(BarGroup("Jan", 3000, 2000), BarGroup("Feb", 2800, 3100), BarGroup("Mär", 3200, 1500)),
                selectedIndex = 1,
            )
            TrendLineChart(values = listOf(100, 300, 250, 500, 420, 700))
        }
    }
}
