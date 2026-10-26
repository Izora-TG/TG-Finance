package com.tgfinance.app.data.local

import android.content.Context
import androidx.annotation.StringRes
import com.tgfinance.app.R
import com.tgfinance.app.data.local.entity.AccountEntity
import com.tgfinance.app.data.local.entity.CategoryEntity
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.ui.theme.FinanceColors
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.CategoryType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts the default categories and accounts on first start. Fixed ids make the seed
 * idempotent across devices: two fresh installs sync without creating duplicates.
 */
@Singleton
class DefaultDataSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TgDatabase,
    private val settings: SettingsRepository,
) {
    private data class DefaultCategory(val id: String, @StringRes val name: Int, val type: CategoryType, val icon: String, val color: Long)

    private val categories = listOf(
        DefaultCategory("default-cat-groceries", R.string.cat_groceries, CategoryType.EXPENSE, "cart", 0xFF22C55E),
        DefaultCategory("default-cat-dining", R.string.cat_dining, CategoryType.EXPENSE, "restaurant", 0xFFF97316),
        DefaultCategory("default-cat-housing", R.string.cat_housing, CategoryType.EXPENSE, "home", 0xFF8B5CF6),
        DefaultCategory("default-cat-utilities", R.string.cat_utilities, CategoryType.EXPENSE, "bolt", 0xFFEAB308),
        DefaultCategory("default-cat-transport", R.string.cat_transport, CategoryType.EXPENSE, "car", 0xFF3B82F6),
        DefaultCategory("default-cat-health", R.string.cat_health, CategoryType.EXPENSE, "health", 0xFFEF4444),
        DefaultCategory("default-cat-leisure", R.string.cat_leisure, CategoryType.EXPENSE, "games", 0xFFEC4899),
        DefaultCategory("default-cat-shopping", R.string.cat_shopping, CategoryType.EXPENSE, "bag", 0xFFA855F7),
        DefaultCategory("default-cat-subscriptions", R.string.cat_subscriptions, CategoryType.EXPENSE, "subscriptions", 0xFF6366F1),
        DefaultCategory("default-cat-insurance", R.string.cat_insurance, CategoryType.EXPENSE, "shield", 0xFF14B8A6),
        DefaultCategory("default-cat-education", R.string.cat_education, CategoryType.EXPENSE, "school", 0xFF0EA5E9),
        DefaultCategory("default-cat-travel", R.string.cat_travel, CategoryType.EXPENSE, "flight", 0xFF06B6D4),
        DefaultCategory("default-cat-gifts", R.string.cat_gifts, CategoryType.EXPENSE, "gift", 0xFFF43F5E),
        DefaultCategory("default-cat-other-expense", R.string.cat_other, CategoryType.EXPENSE, "more", 0xFF94A3B8),
        DefaultCategory("default-cat-salary", R.string.cat_salary, CategoryType.INCOME, "work", 0xFF4ADE80),
        DefaultCategory("default-cat-side-income", R.string.cat_side_income, CategoryType.INCOME, "money", 0xFF34D399),
        DefaultCategory("default-cat-interest", R.string.cat_interest, CategoryType.INCOME, "trending", 0xFF2DD4BF),
        DefaultCategory("default-cat-gifts-income", R.string.cat_gifts_received, CategoryType.INCOME, "gift", 0xFFFB7185),
        DefaultCategory("default-cat-other-income", R.string.cat_other_income, CategoryType.INCOME, "more", 0xFF94A3B8),
    )

    suspend fun seedIfNeeded() {
        if (settings.settings.first().defaultsSeeded) return
        val categoryEntities = categories.mapIndexed { index, c ->
            CategoryEntity(
                id = c.id,
                name = context.getString(c.name),
                type = c.type,
                icon = c.icon,
                color = c.color.toInt(),
                isDefault = true,
                sortOrder = index,
                updatedAt = SEED_TIMESTAMP,
                deviceId = SEED_DEVICE,
                dirty = false,
            )
        }
        val accounts = listOf(
            AccountEntity(
                id = "default-account-checking",
                name = context.getString(R.string.account_default_checking),
                type = AccountType.CHECKING,
                initialBalance = 0,
                color = FinanceColors.PrimaryArgb.toInt(),
                icon = "bank",
                sortOrder = 0,
                updatedAt = SEED_TIMESTAMP,
                deviceId = SEED_DEVICE,
            ),
            AccountEntity(
                id = "default-account-cash",
                name = context.getString(R.string.account_default_cash),
                type = AccountType.CASH,
                initialBalance = 0,
                color = 0xFF22C55E.toInt(),
                icon = "cash",
                sortOrder = 1,
                updatedAt = SEED_TIMESTAMP,
                deviceId = SEED_DEVICE,
            ),
        )
        db.categoryDao().insertIgnore(categoryEntities)
        db.accountDao().insertIgnore(accounts)
        settings.setDefaultsSeeded()
    }

    companion object {
        /** Seed rows are older than any user edit, so every real change wins during sync. */
        const val SEED_TIMESTAMP = 0L
        const val SEED_DEVICE = "seed"
    }
}
