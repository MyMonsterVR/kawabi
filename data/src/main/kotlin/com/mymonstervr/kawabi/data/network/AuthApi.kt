package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.LoginRequest
import com.mymonstervr.kawabi.data.network.dto.LoginResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthApi(
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
    private val dispatchers: AppDispatchers,
) {
    suspend fun login(email: String, password: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(LoginRequest(email, password))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BASE_URL/auth/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401) error("Incorrect email or password")
                    error("Login failed (HTTP ${response.code}), try again")
                }
                val loginResponse = networkJson.decodeFromString(
                    LoginResponse.serializer(),
                    response.body.string(),
                )
                tokenStore.saveToken(loginResponse.token)
            }
        }
    }

    suspend fun logout(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/auth/logout")
                .post("".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { }
        }.also {
            tokenStore.clearToken()
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
