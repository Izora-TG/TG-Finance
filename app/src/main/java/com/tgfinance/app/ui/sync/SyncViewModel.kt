package com.tgfinance.app.ui.sync

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.SyncConflictEntity
import com.tgfinance.app.data.sync.SyncManager
import com.tgfinance.app.data.sync.SyncStatus
import com.tgfinance.core.sync.SyncException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncForm(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
)

data class SyncUiState(
    val status: SyncStatus = SyncStatus(),
    val form: SyncForm = SyncForm(),
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    val conflicts: List<SyncConflictEntity> = emptyList(),
)

@StringRes
fun SyncException.Kind.messageRes(): Int = when (this) {
    SyncException.Kind.NETWORK -> R.string.sync_error_network
    SyncException.Kind.UNAUTHORIZED -> R.string.sync_error_unauthorized
    SyncException.Kind.SERVER -> R.string.sync_error_server
    SyncException.Kind.ENCRYPTION -> R.string.sync_error_encryption
    SyncException.Kind.CONFIGURATION -> R.string.sync_error_configuration
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val sync: SyncManager,
    db: TgDatabase,
) : ViewModel() {

    private val form = MutableStateFlow(SyncForm())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<SyncUiState> = combine(
        sync.status,
        form,
        busy,
        message,
        db.syncConflictDao().observeRecent(),
    ) { status, f, isBusy, msg, conflicts ->
        SyncUiState(status, f, isBusy, msg, conflicts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun updateForm(transform: (SyncForm) -> SyncForm) = form.update(transform)

    fun signIn(register: Boolean) = execute(if (register) R.string.sync_registered else R.string.sync_signed_in) {
        val f = form.value
        sync.signIn(f.serverUrl, f.username, f.password, register)
        form.update { it.copy(password = "") }
        sync.syncNow().getOrThrow()
    }

    fun syncNow() = execute(R.string.sync_done) { sync.syncNow().getOrThrow() }

    fun signOut() = execute(R.string.sync_signed_out) { sync.signOut() }

    fun enableEncryption(password: String) = execute(R.string.sync_encryption_enabled) {
        sync.enableEncryption(password.toCharArray())
        sync.syncNow().getOrThrow()
    }

    fun unlockEncryption(password: String) = execute(R.string.sync_encryption_unlocked) {
        sync.unlockEncryption(password.toCharArray())
        sync.syncNow().getOrThrow()
    }

    fun disableEncryption() = execute(R.string.sync_encryption_disabled) {
        sync.disableEncryption()
        sync.syncNow().getOrThrow()
    }

    fun messageShown() {
        message.value = null
    }

    private fun execute(@StringRes success: Int, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = try {
                block()
                success
            } catch (e: SyncException) {
                e.kind.messageRes()
            } catch (e: Exception) {
                R.string.sync_error_server
            } finally {
                busy.value = false
            }
        }
    }
}
