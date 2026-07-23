package com.mymonstervr.kawabi.data.track.kitsu

import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.dto.KitsuOAuth
import com.mymonstervr.kawabi.data.track.trackerJson
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Adds the Kitsu bearer token to authenticated requests and handles refresh:
 * synchronized (Kitsu single-uses/rotates refresh tokens, so a racing second
 * refresh invalidates the first), explicit 401-on-refresh detection, never
 * reuses a stale access token.
 */
class KitsuInterceptor(private val tokenStore: TrackerTokenStore) : Interceptor {

    private val trackerId = TrackerTokenStore.TRACKER_KITSU

    @Volatile
    private var oauth: KitsuOAuth? = tokenStore.getSession(trackerId)?.let(::decode)

    private val tokenExpired get() = tokenStore.isAuthExpired(trackerId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) throw KitsuTokenExpired()

        val originalRequest = chain.request()
        if (oauth?.isExpired() == true) refreshToken(chain)

        val currentOAuth = oauth ?: throw KitsuTokenExpired()
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${currentOAuth.accessToken}")
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json")
            .build()
        return chain.proceed(authRequest)
    }

    fun setOAuth(newOAuth: KitsuOAuth?) {
        oauth = newOAuth
        if (newOAuth != null) tokenStore.saveSession(trackerId, encode(newOAuth)) else tokenStore.clearSession(trackerId)
    }

    private fun refreshToken(chain: Interceptor.Chain): KitsuOAuth = synchronized(this) {
        if (tokenExpired) throw KitsuTokenExpired()
        oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

        val refreshToken = oauth?.refreshToken ?: throw KitsuTokenExpired()
        val response = try {
            chain.proceed(KitsuApi.refreshTokenRequest(refreshToken))
        } catch (_: Throwable) {
            throw KitsuTokenRefreshFailed()
        }

        if (response.code == 401) {
            response.close()
            tokenStore.setAuthExpired(trackerId)
            throw KitsuTokenExpired()
        }

        return runCatching {
            if (response.isSuccessful) trackerJson.decodeFromString(KitsuOAuth.serializer(), response.body.string()) else null
        }
            .getOrNull()
            ?.also { setOAuth(it) }
            ?: throw KitsuTokenRefreshFailed()
    }

    private fun decode(json: String): KitsuOAuth? =
        runCatching { trackerJson.decodeFromString(KitsuOAuth.serializer(), json) }.getOrNull()

    private fun encode(oauth: KitsuOAuth): String = trackerJson.encodeToString(KitsuOAuth.serializer(), oauth)
}

class KitsuTokenRefreshFailed : IOException("Kitsu: Failed to refresh account token")
class KitsuTokenExpired : IOException("Kitsu: Login has expired")
