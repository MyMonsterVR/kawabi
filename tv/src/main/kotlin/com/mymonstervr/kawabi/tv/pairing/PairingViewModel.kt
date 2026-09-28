package com.mymonstervr.kawabi.tv.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.PairingApi
import com.mymonstervr.kawabi.data.network.PairingPollResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    data object Requesting : PairingUiState
    data class Showing(
        val qrPayload: String,
        val displayCode: String,
        val pollSecret: String,
        val expiresAt: Long,
        val pollIntervalMs: Long,
        val autoRefreshCount: Int,
    ) : PairingUiState
    data object Approved : PairingUiState
    data object IdleTimeout : PairingUiState
    data class Error(val message: String) : PairingUiState
}

private const val MAX_AUTO_REFRESHES = 3
private const val RATE_LIMITED_INTERVAL_MS = 10_000L
private const val INITIAL_FAILURE_BACKOFF_MS = 2_000L
private const val MAX_FAILURE_BACKOFF_MS = 15_000L

/**
 * Drives the TV's QR-pairing screen. See PLAN Phase 5/backend's internal/db/pairing.go for
 * the state machine this mirrors client-side: request a code, poll until a phone approves it
 * (token is saved by [PairingApi.pollPairing] itself, this class never touches it directly),
 * auto-refresh an expired code up to [MAX_AUTO_REFRESHES] times unattended, then stop and ask
 * for input rather than polling forever. [pause]/[resume] let the screen stop polling while
 * backgrounded (screensaver, app switch) without losing the current code/countdown.
 */
class PairingViewModel(
    private val pairingApi: PairingApi,
) : ViewModel() {

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Requesting)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var paused = false

    init {
        requestNewCode(autoRefreshCount = 0)
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        if (!paused) return
        paused = false
    }

    fun retry() {
        requestNewCode(autoRefreshCount = 0)
    }

    private fun requestNewCode(autoRefreshCount: Int) {
        pollJob?.cancel()
        _state.value = PairingUiState.Requesting
        viewModelScope.launch {
            pairingApi.createPairing(deviceName = Build.MODEL ?: "Android TV").fold(
                onSuccess = { created ->
                    val showing = PairingUiState.Showing(
                        qrPayload = created.qr_payload,
                        displayCode = created.display_code,
                        pollSecret = created.poll_secret,
                        expiresAt = created.expires_at,
                        pollIntervalMs = created.poll_interval_ms,
                        autoRefreshCount = autoRefreshCount,
                    )
                    _state.value = showing
                    startPolling(showing)
                },
                onFailure = { e -> _state.value = PairingUiState.Error(e.message ?: "Couldn't reach the server") },
            )
        }
    }

    private fun startPolling(initial: PairingUiState.Showing) {
        pollJob = viewModelScope.launch {
            var interval = initial.pollIntervalMs
            var failureBackoffMs = INITIAL_FAILURE_BACKOFF_MS
            while (isActive) {
                if (paused) {
                    delay(500)
                    continue
                }
                val current = _state.value as? PairingUiState.Showing ?: return@launch
                if (System.currentTimeMillis() >= current.expiresAt) {
                    handleExpiry(current.autoRefreshCount)
                    return@launch
                }
                delay(interval)
                pairingApi.pollPairing(current.pollSecret).fold(
                    onSuccess = { result ->
                        failureBackoffMs = INITIAL_FAILURE_BACKOFF_MS
                        when (result) {
                            is PairingPollResult.Approved -> _state.value = PairingUiState.Approved
                            PairingPollResult.Pending -> {}
                            PairingPollResult.Expired -> {
                                handleExpiry(current.autoRefreshCount)
                                return@launch
                            }
                            PairingPollResult.Unknown -> {
                                requestNewCode(autoRefreshCount = current.autoRefreshCount)
                                return@launch
                            }
                            PairingPollResult.RateLimited -> interval = RATE_LIMITED_INTERVAL_MS
                        }
                    },
                    onFailure = {
                        delay(failureBackoffMs)
                        failureBackoffMs = (failureBackoffMs * 2).coerceAtMost(MAX_FAILURE_BACKOFF_MS)
                    },
                )
            }
        }
    }

    private fun handleExpiry(autoRefreshCount: Int) {
        if (autoRefreshCount >= MAX_AUTO_REFRESHES) {
            pollJob?.cancel()
            _state.value = PairingUiState.IdleTimeout
        } else {
            requestNewCode(autoRefreshCount = autoRefreshCount + 1)
        }
    }
}
