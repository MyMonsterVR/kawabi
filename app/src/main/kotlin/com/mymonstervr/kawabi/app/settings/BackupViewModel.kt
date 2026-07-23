package com.mymonstervr.kawabi.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupOpState {
    data object Idle : BackupOpState
    data object Running : BackupOpState
    data class Success(val message: String) : BackupOpState
    data class Error(val message: String) : BackupOpState
}

class BackupViewModel(private val backupManager: BackupManager) : ViewModel() {

    private val _state = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val state: StateFlow<BackupOpState> = _state.asStateFlow()

    fun export(onJson: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = BackupOpState.Running
            runCatching { backupManager.export() }
                .onSuccess { json ->
                    onJson(json)
                    _state.value = BackupOpState.Success("Backup ready to save")
                }
                .onFailure { _state.value = BackupOpState.Error(it.message ?: "Export failed") }
        }
    }

    fun import(json: String) {
        viewModelScope.launch {
            _state.value = BackupOpState.Running
            backupManager.import(json)
                .onSuccess {
                    _state.value = BackupOpState.Success(
                        "Imported ${it.mangaImported} manga, ${it.chaptersImported} new chapters, ${it.categoriesImported} categories",
                    )
                }
                .onFailure { _state.value = BackupOpState.Error(it.message ?: "Import failed") }
        }
    }

    fun clearState() {
        _state.value = BackupOpState.Idle
    }
}
