package com.mymonstervr.kawabi.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AuthApi
import com.mymonstervr.kawabi.data.network.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvSettingsViewModel(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email.asStateFlow()

    init {
        viewModelScope.launch {
            authApi.me().onSuccess { _email.value = it.email }
        }
    }

    /** Only kills this TV's own session -- the phone that paired it stays logged in. */
    fun signOut() {
        viewModelScope.launch {
            authApi.logout()
            tokenStore.clearToken()
        }
    }
}
