package com.tgfinance.app.ui.components

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tgfinance.app.R
import com.tgfinance.app.ui.theme.FinanceColors
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.util.DateFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selected: String,
    color: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FinanceIcons.all.forEach { (key, icon) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(if (isSelected) Modifier.border(2.dp, color, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = key, tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FinanceColors.Palette.forEach { argb ->
            ColorSwatch(color = Color(argb.toInt()), selected = argb.toInt() == selected, onClick = { onSelect(argb.toInt()) })
        }
    }
}

/** Large amount input with currency suffix and decimal keyboard. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.field_amount),
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == ',' || it == '.' }) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.headlineMedium,
        suffix = { Text(LocalMoneyFormatter.current.symbol, style = MaterialTheme.typography.headlineSmall) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Chips "Heute" / "Gestern" plus a calendar for any other date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChips(
    date: LocalDate,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = date == today, onClick = { onDateChange(today) }, label = { Text(stringResource(R.string.date_today)) })
        FilterChip(
            selected = date == today.minusDays(1),
            onClick = { onDateChange(today.minusDays(1)) },
            label = { Text(stringResource(R.string.date_yesterday)) },
        )
        val isOther = date != today && date != today.minusDays(1)
        AssistChip(
            onClick = { showPicker = true },
            label = { Text(if (isOther) date.format(DateFormats.medium) else stringResource(R.string.date_pick)) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
        )
    }
    if (showPicker) {
        DatePickerDialogWrapper(
            initial = date,
            onDismiss = { showPicker = false },
            onConfirm = {
                onDateChange(it)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogWrapper(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Optional date field (e.g. end date, goal deadline) with a clear option. */
@Composable
fun OptionalDateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AssistChip(
            onClick = { showPicker = true },
            label = { Text(date?.let { "$label: ${it.format(DateFormats.medium)}" } ?: label) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
        )
        if (date != null) {
            TextButton(onClick = { onDateChange(null) }) { Text(stringResource(R.string.action_remove)) }
        }
    }
    if (showPicker) {
        DatePickerDialogWrapper(
            initial = date ?: LocalDate.now(),
            onDismiss = { showPicker = false },
            onConfirm = {
                onDateChange(it)
                showPicker = false
            },
        )
    }
}

@Preview
@Composable
private fun PickersPreview() {
    TgFinanceTheme {
        androidx.compose.foundation.layout.Column {
            AmountField(value = "12,50", onValueChange = {})
            DateChips(date = LocalDate.of(2024, 5, 15), today = LocalDate.of(2024, 5, 15), onDateChange = {})
            ColorPicker(selected = FinanceColors.PrimaryArgb.toInt(), onSelect = {})
            IconPicker(selected = "cart", color = FinanceColors.Primary, onSelect = {})
        }
    }
}
