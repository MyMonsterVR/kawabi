package com.mymonstervr.kawabi.data.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val sessionExpiryNotifier: SessionExpiryNotifier,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.getToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            tokenStore.clearToken()
            sessionExpiryNotifier.notifyExpired()
        }
        return response
    }
}
