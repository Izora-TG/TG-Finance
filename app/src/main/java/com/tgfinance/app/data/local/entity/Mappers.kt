package com.tgfinance.app.data.local.entity

import com.tgfinance.core.model.Account
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.RecurringRule
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.Transaction
import java.time.LocalDate

/** Write metadata applied to every local change. */
data class Stamp(
    val now: Long,
    val deviceId: String,
)

fun AccountEntity.toModel() = Account(id, name, type, initialBalance, color, icon, sortOrder, archived)

fun Account.toEntity(stamp: Stamp, deleted: Boolean = false) = AccountEntity(
    id = id, name = name, type = type, initialBalance = initialBalance, color = color, icon = icon,
    sortOrder = sortOrder, archived = archived,
    updatedAt = stamp.now, deleted = deleted, deviceId = stamp.deviceId, dirty = true,
)

fun CategoryEntity.toModel() = Category(id, name, type, icon, color, isDefault, sortOrder)

fun Category.toEntity(stamp: Stamp, deleted: Boolean = false) = CategoryEntity(
    id = id, name = name, type = type, icon = icon, color = color, isDefault = isDefault, sortOrder = sortOrder,
    updatedAt = stamp.now, deleted = deleted, deviceId = stamp.deviceId, dirty = true,
)

fun TransactionEntity.toModel() = Transaction(
    id = id, type = type, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, date = LocalDate.ofEpochDay(date), note = note,
    recurringId = recurringId, createdAt = createdAt,
)

fun Transaction.toEntity(stamp: Stamp, deleted: Boolean = false) = TransactionEntity(
    id = id, type = type, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, date = date.toEpochDay(), note = note, recurringId = recurringId,
    createdAt = if (createdAt == 0L) stamp.now else createdAt,
    updatedAt = stamp.now, deleted = deleted, deviceId = stamp.deviceId, dirty = true,
)

fun BudgetEntity.toModel() = Budget(id, categoryId, amount)

fun Budget.toEntity(stamp: Stamp, deleted: Boolean = false) = BudgetEntity(
    id = id, categoryId = categoryId, amount = amount,
    updatedAt = stamp.now, deleted = deleted, deviceId = stamp.deviceId, dirty = true,
)

fun RecurringEntity.toModel() = RecurringRule(
    id = id, type = type, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, note = note, frequency = frequency, interval = interval,
    startDate = LocalDate.ofEpochDay(startDate), endDate = endDate?.let(LocalDate::ofEpochDay),
    nextDate = LocalDate.ofEpochDay(nextDate), active = active,
)

fun RecurringRule.toEntity(stamp: Stamp, deleted: Boolean = false) = RecurringEntity(
    id = id, type = type, amount = amount, accountId = accountId, toAccountId = toAccountId,
    categoryId = categoryId, note = note, frequency = frequency, interval = interval,
    startDate = startDate.toEpochDay(), endDate = endDate?.toEpochDay(), nextDate = nextDate.toEpochDay(),
    active = active,
    updatedAt = stamp.now, deleted = deleted, deviceId = stamp.deviceId, dirty = true,
)

fun GoalEntity.toModel() = SavingsGoal(
    id = id, name = name, target = target, saved = saved,
    targetDate = targetDate?.let(LocalDate::ofEpochDay), color = color, icon = icon,
)

fun SavingsGoal.toEntity(stamp: Stamp, deleted: Boolean = false) = GoalEntity(
    id = id, name = name, target = target, saved = saved, targetDate = targetDate?.toEpochDay(),
    color = color, icon = icon,
    updatedAt = stamp.now, deleted = deleted, deviceId = stamp.deviceId, dirty = true,
)
