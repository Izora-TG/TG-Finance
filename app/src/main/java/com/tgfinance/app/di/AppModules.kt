package com.tgfinance.app.di

import android.content.Context
import androidx.room.Room
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.BudgetRepository
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.data.repository.GoalRepository
import com.tgfinance.app.data.repository.RecurringRepository
import com.tgfinance.app.data.repository.RoomAccountRepository
import com.tgfinance.app.data.repository.RoomBudgetRepository
import com.tgfinance.app.data.repository.RoomCategoryRepository
import com.tgfinance.app.data.repository.RoomGoalRepository
import com.tgfinance.app.data.repository.RoomRecurringRepository
import com.tgfinance.app.data.repository.RoomTransactionRepository
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.data.settings.DataStoreSettingsRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.app.util.SystemTimeProvider
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.app.util.UuidGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TgDatabase =
        Room.databaseBuilder(context, TgDatabase::class.java, TgDatabase.NAME).build()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun bindSettings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun bindAccounts(impl: RoomAccountRepository): AccountRepository
    @Binds abstract fun bindCategories(impl: RoomCategoryRepository): CategoryRepository
    @Binds abstract fun bindTransactions(impl: RoomTransactionRepository): TransactionRepository
    @Binds abstract fun bindBudgets(impl: RoomBudgetRepository): BudgetRepository
    @Binds abstract fun bindRecurring(impl: RoomRecurringRepository): RecurringRepository
    @Binds abstract fun bindGoals(impl: RoomGoalRepository): GoalRepository
    @Binds abstract fun bindTime(impl: SystemTimeProvider): TimeProvider
    @Binds abstract fun bindIds(impl: UuidGenerator): IdGenerator
}
