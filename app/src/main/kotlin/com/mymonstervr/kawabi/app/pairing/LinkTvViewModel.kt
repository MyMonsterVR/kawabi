package com.mymonstervr.kawabi.app.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.PairingApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QR_PREFIX = "kawabi-pair:v1:"

sealed interface LinkTvState {
    data object Idle : LinkTvState
    data class Confirm(val displayCode: String) : LinkTvState
    data object Approving : LinkTvState
    data class Done(val deviceName: String) : LinkTvState
    data class Error(val message: String) : LinkTvState
}

/**
 * Backs the phone's "Link a TV" screen. Either a scanned QR ([onScanned]) or a manually
 * typed code ([onManualCode]) leads to the same confirm step before calling the backend, so
 * approving the wrong/malicious code doesn't silently link a stranger's TV to this account.
 */
class LinkTvViewModel(
    private val pairingApi: PairingApi,
) : ViewModel() {

    private val _state = MutableStateFlow<LinkTvState>(LinkTvState.Idle)
    val state: StateFlow<LinkTvState> = _state.asStateFlow()

    private var pendingCode: String? = null

    /**
     * A scan is a raw QR payload, not user-chosen text -- unlike manual entry, a real kawabi
     * TV code always carries [QR_PREFIX], so anything else (a random QR, someone else's app's
     * code) is rejected outright here instead of reaching the confirm step with a scanned
     * string that merely happens to be non-blank.
     */
    fun onScanned(raw: String) {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(QR_PREFIX)) {
            _state.value = LinkTvState.Error("That doesn't look like a kawabi TV code")
            return
        }
        submitCode(trimmed.removePrefix(QR_PREFIX))
    }

    fun onManualCode(raw: String) {
        submitCode(raw.trim())
    }

    private fun submitCode(code: String) {
        if (code.isBlank()) {
            _state.value = LinkTvState.Error("That doesn't look like a kawabi TV code")
            return
        }
        pendingCode = code
        _state.value = LinkTvState.Confirm(displayCode(code))
    }

    fun confirm() {
        val code = pendingCode ?: return
        _state.value = LinkTvState.Approving
        viewModelScope.launch {
            pairingApi.approvePairing(code).fold(
                onSuccess = { response -> _state.value = LinkTvState.Done(response.device_name) },
                onFailure = { e -> _state.value = LinkTvState.Error(e.message ?: "Couldn't link the TV") },
            )
        }
    }

    fun cancel() {
        pendingCode = null
        _state.value = LinkTvState.Idle
    }

    fun reset() {
        pendingCode = null
        _state.value = LinkTvState.Idle
    }

    // Purely cosmetic (backend's normalizePairingCode tolerates dashes/case on its own) --
    // this just formats whatever the user scanned/typed into the same "XXXX-XXXX" shape the
    // TV screen shows, so the confirm step reads back the code they'd expect to see.
    private fun displayCode(code: String): String {
        val stripped = code.replace("-", "").uppercase()
        return if (stripped.length == 8) "${stripped.take(4)}-${stripped.takeLast(4)}" else code.uppercase()
    }
}
