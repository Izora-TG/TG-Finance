package com.tgfinance.app.data.repository

import androidx.room.withTransaction
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.toEntity
import com.tgfinance.app.data.local.entity.toModel
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeCategories(type: CategoryType): Flow<List<Category>>
    /** Includes deleted categories, used to label old bookings. */
    fun observeAllIncludingDeleted(): Flow<List<Category>>
    /** Categories of [type], most used (since [since]) first – speeds up quick entry. */
    fun observeByUsage(type: CategoryType, since: LocalDate): Flow<List<Category>>
    suspend fun get(id: String): Category?
    suspend fun save(category: Category): String
    /** Soft-deletes the category and its budget; bookings keep the reference. */
    suspend fun delete(id: String)
}

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val db: TgDatabase,
    private val stamps: StampFactory,
    private val ids: IdGenerator,
) : CategoryRepository {

    private val dao = db.categoryDao()

    override fun observeCategories(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeCategories(type: CategoryType): Flow<List<Category>> =
        dao.observeByType(type).map { list -> list.map { it.toModel() } }

    override fun observeAllIncludingDeleted(): Flow<List<Category>> =
        dao.observeAllIncludingDeleted().map { list -> list.map { it.toModel() } }

    override fun observeByUsage(type: CategoryType, since: LocalDate): Flow<List<Category>> =
        combine(observeCategories(type), dao.observeUsage(since.toEpochDay())) { categories, usage ->
            val uses = usage.associate { it.categoryId to it.uses }
            categories.sortedWith(compareByDescending<Category> { uses[it.id] ?: 0 }.thenBy { it.sortOrder })
        }

    override suspend fun get(id: String): Category? = dao.find(id)?.toModel()

    override suspend fun save(category: Category): String {
        val withId = if (category.id.isBlank()) {
            category.copy(id = ids.newId(), sortOrder = dao.maxSortOrder() + 1)
        } else {
            category
        }
        dao.upsert(withId.toEntity(stamps.create()))
        return withId.id
    }

    override suspend fun delete(id: String) {
        val stamp = stamps.create()
        db.withTransaction {
            val category = dao.find(id) ?: return@withTransaction
            dao.upsert(category.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
            val budgetDao = db.budgetDao()
            budgetDao.findByCategory(id)?.let {
                budgetDao.upsert(it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
            }
        }
    }
}
