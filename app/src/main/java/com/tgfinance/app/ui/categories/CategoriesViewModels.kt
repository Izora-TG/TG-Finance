package com.tgfinance.app.ui.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.ui.theme.FinanceColors
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val type: CategoryType = CategoryType.EXPENSE,
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    private val type = MutableStateFlow(CategoryType.EXPENSE)

    val uiState: StateFlow<CategoriesUiState> = combine(type, repository.observeCategories()) { t, all ->
        CategoriesUiState(t, all.filter { it.type == t })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun setType(value: CategoryType) {
        type.value = value
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

data class CategoryEditUiState(
    val id: String? = null,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val icon: String = "cart",
    val color: Int = FinanceColors.PrimaryArgb.toInt(),
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    @StringRes val error: Int? = null,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoryEditUiState())
    val uiState: StateFlow<CategoryEditUiState> = _state

    private var loadedKey: String? = null

    fun load(id: String?, type: CategoryType) {
        val key = "$id|$type"
        if (loadedKey == key) return
        loadedKey = key
        if (id == null) {
            _state.value = CategoryEditUiState(type = type, icon = if (type == CategoryType.INCOME) "money" else "cart")
            return
        }
        viewModelScope.launch {
            val category = repository.get(id) ?: return@launch
            _state.value = CategoryEditUiState(
                id = category.id,
                name = category.name,
                type = category.type,
                icon = category.icon,
                color = category.color,
                isDefault = category.isDefault,
                sortOrder = category.sortOrder,
            )
        }
    }

    fun update(transform: (CategoryEditUiState) -> CategoryEditUiState) = _state.update { transform(it).copy(error = null) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = R.string.error_name_required) }
            return
        }
        viewModelScope.launch {
            repository.save(
                Category(
                    id = s.id.orEmpty(),
                    name = s.name.trim(),
                    type = s.type,
                    icon = s.icon,
                    color = s.color,
                    isDefault = s.isDefault,
                    sortOrder = s.sortOrder,
                ),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
