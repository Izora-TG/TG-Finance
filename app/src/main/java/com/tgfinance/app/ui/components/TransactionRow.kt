package com.tgfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.tgfinance.app.R
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType

@Composable
fun TransactionRow(
    details: TransactionDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    horizontalPadding: Dp = Spacing.l,
) {
    val tx = details.transaction
    val title = when (tx.type) {
        TransactionType.TRANSFER -> stringResource(R.string.transaction_type_transfer)
        else -> details.category?.name ?: stringResource(R.string.category_none)
    }
    val subtitle = when (tx.type) {
        TransactionType.TRANSFER -> "${details.account?.name.orEmpty()} → ${details.toAccount?.name.orEmpty()}"
        else -> listOfNotNull(tx.note.takeIf { it.isNotBlank() }, details.account?.name).joinToString(" · ")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tx.type == TransactionType.TRANSFER) {
            ColoredIcon(Icons.Outlined.SwapHoriz, MaterialTheme.colorScheme.secondary)
        } else {
            val category = details.category
            ColoredIcon(
                icon = FinanceIcons.forKey(category?.icon ?: "more"),
                color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.m),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tx.recurringId != null) {
            Icon(
                Icons.Outlined.Autorenew,
                contentDescription = stringResource(R.string.recurring_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.s),
            )
        }
        AmountText(cents = tx.amount, type = tx.type, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview
@Composable
private fun TransactionRowPreview() {
    TgFinanceTheme {
        Column {
            PreviewData.transactions.forEach { TransactionRow(it, onClick = {}) }
        }
    }
}
