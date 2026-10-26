package com.tgfinance.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tgfinance.app.R
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.core.model.TransactionType

/** Rounded content card used for every dashboard section. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            if (title != null || action != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    action?.invoke()
                }
            }
            content()
        }
    }
}

/** Card whose details can be expanded – keeps screens calm while hiding nothing. */
@Composable
fun ExpandableCard(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    summary: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    SectionCard(
        modifier = modifier,
        title = title,
        action = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(Spacing.xs),
            )
        },
    ) {
        summary()
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(top = Spacing.m)) { details() }
        }
    }
}

@Composable
fun transactionColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> MaterialTheme.finance.income
    TransactionType.EXPENSE -> MaterialTheme.finance.expense
    TransactionType.TRANSFER -> MaterialTheme.colorScheme.secondary
}

/** Formats an amount with sign and colour according to the booking type. */
@Composable
fun AmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color? = null,
) {
    val formatter = LocalMoneyFormatter.current
    val text = when (type) {
        TransactionType.INCOME -> formatter.format(cents, showPlusSign = true)
        TransactionType.EXPENSE -> formatter.format(-cents)
        else -> formatter.format(cents)
    }
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = color ?: type?.let { transactionColor(it) } ?: MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

/** Round, tinted icon used for categories, accounts and goals. */
@Composable
fun ColoredIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun ColoredIcon(iconKey: String, colorArgb: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) =
    ColoredIcon(FinanceIcons.forKey(iconKey), Color(colorArgb), modifier, size)

/** Rounded progress bar; turns amber at 80 % and red above 100 %. */
@Composable
fun BudgetBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = progressColor(ratio),
) {
    val animated by animateFloatAsState(targetValue = ratio.coerceIn(0f, 1f), label = "budget")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

@Composable
fun progressColor(ratio: Float): Color = when {
    ratio > 1f -> MaterialTheme.finance.expense
    ratio >= 0.8f -> MaterialTheme.finance.warning
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ColoredIcon(icon, MaterialTheme.colorScheme.primary, size = 64.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.finance.expense else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Small label/value pair, e.g. "Einnahmen  +1.200 €". */
@Composable
fun LabeledAmount(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    type: TransactionType? = null,
    alignEnd: Boolean = false,
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        AmountText(cents = cents, type = type)
    }
}

/** Colour swatch row used in edit screens. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
fun HorizontalSpacer(width: Dp) = Spacer(Modifier.width(width))

@Preview
@Composable
private fun CommonPreview() {
    TgFinanceTheme {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SectionCard(title = "Budget") {
                LabeledAmount(label = "Ausgaben", cents = 12345, type = TransactionType.EXPENSE)
                Spacer(Modifier.height(Spacing.s))
                BudgetBar(ratio = 0.85f)
            }
            ExpandableCard(title = "Details", initiallyExpanded = true, summary = { Text("Zusammenfassung") }) {
                Text("Mehr Informationen")
            }
        }
    }
}
