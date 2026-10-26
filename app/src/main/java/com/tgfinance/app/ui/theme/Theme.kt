package com.tgfinance.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tgfinance.app.data.settings.ThemeMode
import com.tgfinance.core.money.MoneyFormatter

/** Colours that Material 3 has no slot for. */
@Immutable
data class ExtendedColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val textSecondary: Color,
)

private val DarkExtended = ExtendedColors(
    income = FinanceColors.Income,
    incomeContainer = FinanceColors.IncomeContainer,
    expense = FinanceColors.Expense,
    expenseContainer = FinanceColors.ExpenseContainer,
    warning = FinanceColors.Warning,
    warningContainer = FinanceColors.WarningContainer,
    textSecondary = FinanceColors.TextSecondary,
)

private val LightExtended = ExtendedColors(
    income = FinanceColors.LightIncome,
    incomeContainer = Color(0xFFE2F6EC),
    expense = FinanceColors.LightExpense,
    expenseContainer = Color(0xFFFBE4E6),
    warning = Color(0xFFB7791F),
    warningContainer = Color(0xFFFDF0D9),
    textSecondary = FinanceColors.LightTextSecondary,
)

private val DarkScheme = darkColorScheme(
    primary = FinanceColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FinanceColors.PrimaryContainer,
    onPrimaryContainer = FinanceColors.OnPrimaryContainer,
    secondary = FinanceColors.PrimaryLight,
    onSecondary = FinanceColors.Background,
    secondaryContainer = FinanceColors.PrimaryContainer,
    onSecondaryContainer = FinanceColors.OnPrimaryContainer,
    tertiary = FinanceColors.PrimaryLighter,
    onTertiary = FinanceColors.Background,
    background = FinanceColors.Background,
    onBackground = FinanceColors.TextPrimary,
    surface = FinanceColors.Background,
    onSurface = FinanceColors.TextPrimary,
    surfaceVariant = FinanceColors.SurfaceHigh,
    onSurfaceVariant = FinanceColors.TextSecondary,
    surfaceContainerLowest = FinanceColors.Background,
    surfaceContainerLow = FinanceColors.Surface,
    surfaceContainer = FinanceColors.Surface,
    surfaceContainerHigh = FinanceColors.SurfaceHigh,
    surfaceContainerHighest = FinanceColors.SurfaceHighest,
    outline = FinanceColors.Outline,
    outlineVariant = FinanceColors.OutlineVariant,
    error = FinanceColors.Error,
    onError = Color.White,
    errorContainer = FinanceColors.ExpenseContainer,
    onErrorContainer = FinanceColors.Expense,
)

private val LightScheme = lightColorScheme(
    primary = FinanceColors.LightPrimary,
    onPrimary = Color.White,
    primaryContainer = FinanceColors.LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF2A0F6B),
    secondary = FinanceColors.Primary,
    secondaryContainer = FinanceColors.LightPrimaryContainer,
    onSecondaryContainer = Color(0xFF2A0F6B),
    background = FinanceColors.LightBackground,
    onBackground = FinanceColors.LightText,
    surface = FinanceColors.LightBackground,
    onSurface = FinanceColors.LightText,
    surfaceVariant = FinanceColors.LightSurfaceHigh,
    onSurfaceVariant = FinanceColors.LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FinanceColors.LightSurface,
    surfaceContainer = FinanceColors.LightSurface,
    surfaceContainerHigh = FinanceColors.LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFE7E3F2),
    outline = FinanceColors.LightOutline,
    outlineVariant = Color(0xFFE7E3F2),
    error = FinanceColors.LightExpense,
)

val FinanceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalFinanceColors = staticCompositionLocalOf { DarkExtended }
val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter("EUR") }

/** Spacing scale used across the app. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun TgFinanceTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    currencyCode: String = "EUR",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val formatter = remember(currencyCode) { MoneyFormatter(currencyCode) }
    CompositionLocalProvider(
        LocalFinanceColors provides if (dark) DarkExtended else LightExtended,
        LocalMoneyFormatter provides formatter,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = FinanceTypography,
            shapes = FinanceShapes,
            content = content,
        )
    }
}

/** Shortcut: `MaterialTheme.finance.income`. */
val MaterialTheme.finance: ExtendedColors
    @Composable get() = LocalFinanceColors.current
