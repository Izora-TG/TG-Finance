package com.tgfinance.app.ui.goals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.repository.GoalRepository
import com.tgfinance.app.ui.theme.FinanceColors
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.money.AmountParser
import com.tgfinance.core.money.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class GoalItem(
    val goal: SavingsGoal,
    /** Amount that has to be saved per month to reach the goal in time, if a date is set. */
    val monthlyNeeded: Long?,
)

data class GoalsUiState(
    val loading: Boolean = true,
    val goals: List<GoalItem> = emptyList(),
) {
    val totalSaved: Long get() = goals.sumOf { it.goal.saved }
    val totalTarget: Long get() = goals.sumOf { it.goal.target }
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goals: GoalRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<GoalsUiState> = goals.observeGoals()
        .map { list -> GoalsUiState(false, list.map { GoalItem(it, monthlyNeeded(it, time.today())) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    /** [deposit] = true adds money, false withdraws. Returns false for invalid input. */
    fun adjust(id: String, amountText: String, deposit: Boolean): Boolean {
        val amount = AmountParser.parse(amountText)?.takeIf { it > 0 } ?: return false
        viewModelScope.launch { goals.adjust(id, if (deposit) amount else -amount) }
        return true
    }

    companion object {
        fun monthlyNeeded(goal: SavingsGoal, today: LocalDate): Long? {
            val date = goal.targetDate ?: return null
            if (goal.isReached) return 0L
            val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), date.withDayOfMonth(1)).coerceAtLeast(1)
            val missing = goal.target - goal.saved
            return (missing + months - 1) / months
        }
    }
}

data class GoalEditUiState(
    val id: String? = null,
    val name: String = "",
    val targetText: String = "",
    val savedText: String = "",
    val targetDate: LocalDate? = null,
    val color: Int = FinanceColors.PrimaryArgb.toInt(),
    val icon: String = "savings",
    @StringRes val error: Int? = null,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = name.isNotBlank() && targetText.isNotBlank()
}

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val goals: GoalRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GoalEditUiState())
    val uiState: StateFlow<GoalEditUiState> = _state

    private var loadedId: String? = "unset"

    fun load(id: String?) {
        if (loadedId == id) return
        loadedId = id
        if (id == null) return
        viewModelScope.launch {
            val goal = goals.get(id) ?: return@launch
            val formatter = MoneyFormatter()
            _state.value = GoalEditUiState(
                id = goal.id,
                name = goal.name,
                targetText = formatter.formatPlain(goal.target),
                savedText = if (goal.saved == 0L) "" else formatter.formatPlain(goal.saved),
                targetDate = goal.targetDate,
                color = goal.color,
                icon = goal.icon,
            )
        }
    }

    fun update(transform: (GoalEditUiState) -> GoalEditUiState) = _state.update { transform(it).copy(error = null) }

    fun save() {
        val s = _state.value
        val target = AmountParser.parse(s.targetText)
        val saved = if (s.savedText.isBlank()) 0L else AmountParser.parse(s.savedText)
        val error = when {
            s.name.isBlank() -> R.string.error_name_required
            target == null || target <= 0 || saved == null -> R.string.error_amount_invalid
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            goals.save(SavingsGoal(s.id.orEmpty(), s.name.trim(), target!!, saved!!, s.targetDate, s.color, s.icon))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            goals.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
