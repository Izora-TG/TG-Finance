package com.tgfinance.app.data.repository

import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.toEntity
import com.tgfinance.app.data.local.entity.toModel
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.core.model.SavingsGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface GoalRepository {
    fun observeGoals(): Flow<List<SavingsGoal>>
    suspend fun get(id: String): SavingsGoal?
    suspend fun save(goal: SavingsGoal): String
    suspend fun delete(id: String)
    /** Adds (positive) or withdraws (negative) money; the saved amount never drops below zero. */
    suspend fun adjust(id: String, delta: Long)
}

@Singleton
class RoomGoalRepository @Inject constructor(
    db: TgDatabase,
    private val stamps: StampFactory,
    private val ids: IdGenerator,
) : GoalRepository {

    private val dao = db.goalDao()

    override fun observeGoals(): Flow<List<SavingsGoal>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun get(id: String): SavingsGoal? = dao.find(id)?.toModel()

    override suspend fun save(goal: SavingsGoal): String {
        require(goal.target > 0) { "Target must be positive" }
        val withId = if (goal.id.isBlank()) goal.copy(id = ids.newId()) else goal
        dao.upsert(withId.toEntity(stamps.create()))
        return withId.id
    }

    override suspend fun delete(id: String) {
        val stamp = stamps.create()
        val entity = dao.find(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
    }

    override suspend fun adjust(id: String, delta: Long) {
        val goal = dao.find(id)?.toModel() ?: return
        val updated = goal.copy(saved = (goal.saved + delta).coerceAtLeast(0))
        dao.upsert(updated.toEntity(stamps.create()))
    }
}
