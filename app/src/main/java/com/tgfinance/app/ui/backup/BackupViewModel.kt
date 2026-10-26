package com.tgfinance.app.ui.backup

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.backup.BackupException
import com.tgfinance.app.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupMessage(@StringRes val text: Int, val count: Int = 0, val isError: Boolean = false)

data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _state.asStateFlow()

    fun exportCsv(uri: Uri) = execute(R.string.backup_csv_done) { backup.exportCsv(uri) }
    fun exportBackup(uri: Uri) = execute(R.string.backup_export_done) { backup.exportBackup(uri) }
    fun importBackup(uri: Uri) = execute(R.string.backup_import_done) { backup.importBackup(uri) }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun execute(@StringRes success: Int, block: suspend () -> Int) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            val message = try {
                BackupMessage(success, block())
            } catch (e: BackupException) {
                BackupMessage(R.string.backup_error_invalid, isError = true)
            } catch (e: Exception) {
                BackupMessage(R.string.backup_error_io, isError = true)
            }
            _state.value = BackupUiState(busy = false, message = message)
        }
    }
}
