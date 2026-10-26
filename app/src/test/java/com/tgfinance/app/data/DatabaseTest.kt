package com.tgfinance.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.repository.StampFactory
import com.tgfinance.app.fakes.FakeSettingsRepository
import com.tgfinance.app.fakes.FakeTimeProvider
import com.tgfinance.app.fakes.SequentialIdGenerator
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import org.junit.After
import org.junit.Before

/** Base class: in-memory Room database plus deterministic time, ids and device id. */
abstract class DatabaseTest {
    protected lateinit var db: TgDatabase
    protected val time = FakeTimeProvider()
    protected val settings = FakeSettingsRepository()
    protected val ids = SequentialIdGenerator()
    protected val stamps = StampFactory(time, settings)

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TgDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    protected fun account(name: String, initial: Long = 0, type: AccountType = AccountType.CHECKING) =
        Account("", name, type, initial, 0, "bank")

    protected fun category(name: String, type: CategoryType = CategoryType.EXPENSE) =
        Category("", name, type, "cart", 0)
}
