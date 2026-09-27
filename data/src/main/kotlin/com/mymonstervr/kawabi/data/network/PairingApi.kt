package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.PairingApproveRequest
import com.mymonstervr.kawabi.data.network.dto.PairingApproveResponse
import com.mymonstervr.kawabi.data.network.dto.PairingCreateRequest
import com.mymonstervr.kawabi.data.network.dto.PairingCreateResponse
import com.mymonstervr.kawabi.data.network.dto.PairingPollRequest
import com.mymonstervr.kawabi.data.network.dto.PairingPollResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface PairingPollResult {
    data object Pending : PairingPollResult
    data class Approved(val token: String) : PairingPollResult
    data object Expired : PairingPollResult
    data object Unknown : PairingPollResult
    data object RateLimited : PairingPollResult
}

/**
 * QR-pairing client (`/auth/pair*` on the backend) -- shared between the phone's
 * "Link a TV" screen ([approvePairing], using its own existing Bearer token) and the
 * TV app's pairing screen ([createPairing]/[pollPairing], unauthenticated). See
 * mihon-sync-server's internal/handler/pairing.go for the exact response shapes this
 * mirrors -- poll's 200/410/404 all carry meaningful bodies, so it's parsed by hand
 * instead of going through BackendApiClient's execute() (which throws on any non-2xx).
 */
class PairingApi(
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
    private val dispatchers: AppDispatchers,
) {
    suspend fun createPairing(deviceName: String): Result<PairingCreateResponse> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(PairingCreateRequest.serializer(), PairingCreateRequest(deviceName))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/auth/pair").post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
                networkJson.decodeFromString(PairingCreateResponse.serializer(), response.body.string())
            }
        }
    }

    suspend fun pollPairing(pollSecret: String): Result<PairingPollResult> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(PairingPollRequest.serializer(), PairingPollRequest(pollSecret))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/auth/pair/poll").post(body).build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    429 -> PairingPollResult.RateLimited
                    404 -> PairingPollResult.Unknown
                    410 -> PairingPollResult.Expired
                    else -> {
                        val parsed = networkJson.decodeFromString(PairingPollResponse.serializer(), response.body.string())
                        when (parsed.status) {
                            "approved" -> {
                                val token = parsed.token ?: error("Approved poll response missing token")
                                tokenStore.saveToken(token)
                                PairingPollResult.Approved(token)
                            }
                            "pending" -> PairingPollResult.Pending
                            "expired" -> PairingPollResult.Expired
                            else -> PairingPollResult.Unknown
                        }
                    }
                }
            }
        }
    }

    /** Phone side -- [code] comes from the QR scan or manual entry; auth is this device's own Bearer token. */
    suspend fun approvePairing(code: String): Result<PairingApproveResponse> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(PairingApproveRequest.serializer(), PairingApproveRequest(code))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/auth/pair/approve").post(body).build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> error("This code doesn't exist")
                    410 -> error("This code has expired -- ask the TV for a new one")
                    409 -> error("Already approved")
                    else -> {
                        if (!response.isSuccessful) error(errorMessageFor(response))
                        networkJson.decodeFromString(PairingApproveResponse.serializer(), response.body.string())
                    }
                }
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
