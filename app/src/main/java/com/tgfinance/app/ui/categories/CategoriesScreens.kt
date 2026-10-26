package com.tgfinance.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.ColorPicker
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.IconPicker
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.core.model.CategoryType

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    onEdit: (String?, CategoryType) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesScreen(state, onBack, viewModel::setType, onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onBack: () -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onEdit: (String?, CategoryType) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null, state.type) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add)) }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CategoryTypeSelector(state.type, onTypeChange, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.categories, key = { it.id }) { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(category.id, category.type) }
                            .padding(horizontal = Spacing.l, vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColoredIcon(category.icon, category.color)
                        Text(
                            category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Spacing.m),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTypeSelector(selected: CategoryType, onSelect: (CategoryType) -> Unit, modifier: Modifier = Modifier) {
    val types = listOf(CategoryType.EXPENSE, CategoryType.INCOME)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
            ) {
                Text(stringResource(if (type == CategoryType.EXPENSE) R.string.label_expenses else R.string.label_income))
            }
        }
    }
}

@Composable
fun CategoryEditRoute(
    id: String?,
    initialType: CategoryType,
    onDone: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(id, initialType) { viewModel.load(id, initialType) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    CategoryEditScreen(state, viewModel::update, viewModel::save, viewModel::delete, onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    state: CategoryEditUiState,
    onChange: ((CategoryEditUiState) -> CategoryEditUiState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.category_add else R.string.category_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(R.string.action_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColoredIcon(state.icon, state.color, size = 56.dp)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { name -> onChange { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                )
            }
            if (state.isNew) {
                CategoryTypeSelector(state.type, { type -> onChange { it.copy(type = type) } })
            }
            Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelLarge)
            ColorPicker(selected = state.color, onSelect = { color -> onChange { it.copy(color = color) } })
            Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
            IconPicker(selected = state.icon, color = Color(state.color), onSelect = { icon -> onChange { it.copy(icon = icon) } })
            state.error?.let { Text(stringResource(it), color = MaterialTheme.finance.expense) }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.action_save)) }
            Spacer(Modifier.height(Spacing.l))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.category_delete_title),
            message = stringResource(R.string.category_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun CategoriesPreview() {
    TgFinanceTheme {
        CategoriesScreen(
            CategoriesUiState(CategoryType.EXPENSE, PreviewData.categories.filter { it.type == CategoryType.EXPENSE }),
            onBack = {},
            onTypeChange = {},
            onEdit = { _, _ -> },
        )
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun CategoryEditPreview() {
    TgFinanceTheme {
        CategoryEditScreen(CategoryEditUiState(name = "Haustiere", icon = "pets"), {}, {}, {}, {})
    }
}
