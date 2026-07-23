package com.mymonstervr.kawabi.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AuthApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.usecase.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authApi: AuthApi,
    private val syncClient: SyncClient,
    private val appScope: CoroutineScope,
    tokenStore: TokenStore,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = tokenStore.isLoggedIn

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(email: String, password: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            authApi.login(email, password)
                .onSuccess { appScope.launch { syncClient.sync() } }
                .onFailure { _error.value = it.message ?: "Login failed" }
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { authApi.logout() }
    }
}
