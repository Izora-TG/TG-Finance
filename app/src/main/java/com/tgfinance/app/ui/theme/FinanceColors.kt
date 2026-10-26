package com.tgfinance.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Central colour definitions. All screens use these via [MaterialTheme.colorScheme] or
 * [LocalFinanceColors]; no colour literals elsewhere in the UI.
 */
object FinanceColors {
    const val PrimaryArgb = 0xFF8B5CF6

    // Dark theme (default)
    val Background = Color(0xFF0F0F14)
    val Surface = Color(0xFF1A1A23)
    val SurfaceHigh = Color(0xFF22222D)
    val SurfaceHighest = Color(0xFF2A2A37)
    val Outline = Color(0xFF34344A)
    val OutlineVariant = Color(0xFF26263A)
    val TextPrimary = Color(0xFFF4F4F8)
    val TextSecondary = Color(0xFFA3A3B8)

    // Violet accents
    val Primary = Color(PrimaryArgb)
    val PrimaryHover = Color(0xFF7C4DEB)
    val PrimaryLight = Color(0xFFA78BFA)
    val PrimaryLighter = Color(0xFFC4B5FD)
    val PrimaryContainer = Color(0xFF2E2350)
    val OnPrimaryContainer = Color(0xFFE4DAFF)

    // Semantic colours – deliberately muted for a calm look
    val Income = Color(0xFF4CC38A)
    val IncomeContainer = Color(0xFF173226)
    val Expense = Color(0xFFEF6F78)
    val ExpenseContainer = Color(0xFF3A1D22)
    val Warning = Color(0xFFF2B65A)
    val WarningContainer = Color(0xFF3A2D16)
    val Error = Color(0xFFFF6B7A)

    // Light theme
    val LightBackground = Color(0xFFF7F6FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceHigh = Color(0xFFF0EDF8)
    val LightOutline = Color(0xFFD6D2E4)
    val LightText = Color(0xFF16161D)
    val LightTextSecondary = Color(0xFF5F5D70)
    val LightPrimary = Color(0xFF7C3AED)
    val LightPrimaryContainer = Color(0xFFEDE5FF)
    val LightIncome = Color(0xFF16955A)
    val LightExpense = Color(0xFFD23F4B)

    /** Colours offered when creating categories, accounts and goals. */
    val Palette: List<Long> = listOf(
        0xFF8B5CF6, 0xFFA855F7, 0xFF6366F1, 0xFF3B82F6, 0xFF0EA5E9, 0xFF06B6D4,
        0xFF14B8A6, 0xFF22C55E, 0xFF84CC16, 0xFFEAB308, 0xFFF97316, 0xFFEF4444,
        0xFFF43F5E, 0xFFEC4899, 0xFF94A3B8, 0xFFC4B5FD,
    )
}
