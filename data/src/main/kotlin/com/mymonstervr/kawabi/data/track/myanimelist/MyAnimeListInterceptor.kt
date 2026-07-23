package com.mymonstervr.kawabi.data.track.myanimelist

import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.dto.MALOAuth
import com.mymonstervr.kawabi.data.track.trackerJson
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Adds the MAL bearer token to authenticated requests and handles refresh:
 * synchronized (concurrent requests must not both fire a refresh), an
 * explicit "auth expired" flag set only when the *refresh* call itself 401s
 * (vs. any other refresh failure), and never falls through with a stale
 * token. Persists directly through [TrackerTokenStore].
 */
class MyAnimeListInterceptor(private val tokenStore: TrackerTokenStore) : Interceptor {

    private val trackerId = TrackerTokenStore.TRACKER_MAL

    @Volatile
    private var oauth: MALOAuth? = tokenStore.getSession(trackerId)?.let(::decode)

    private val tokenExpired get() = tokenStore.isAuthExpired(trackerId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) throw MalTokenExpired()

        val originalRequest = chain.request()
        if (oauth?.isExpired() == true) refreshToken(chain)

        val currentOAuth = oauth ?: throw IOException("MAL: not authenticated")
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${currentOAuth.accessToken}")
            .build()
        return chain.proceed(authRequest)
    }

    /** Called after a fresh browser OAuth exchange, or `null` on logout. */
    fun setOAuth(newOAuth: MALOAuth?) {
        oauth = newOAuth
        if (newOAuth != null) tokenStore.saveSession(trackerId, encode(newOAuth)) else tokenStore.clearSession(trackerId)
    }

    private fun refreshToken(chain: Interceptor.Chain): MALOAuth = synchronized(this) {
        if (tokenExpired) throw MalTokenExpired()
        oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

        val response = try {
            chain.proceed(MyAnimeListApi.refreshTokenRequest(oauth ?: throw MalTokenExpired()))
        } catch (e: MalTokenExpired) {
            throw e
        } catch (_: Throwable) {
            throw MalTokenRefreshFailed()
        }

        if (response.code == 401) {
            response.close()
            tokenStore.setAuthExpired(trackerId)
            throw MalTokenExpired()
        }

        return runCatching {
            if (response.isSuccessful) trackerJson.decodeFromString(MALOAuth.serializer(), response.body.string()) else null
        }
            .getOrNull()
            ?.also { setOAuth(it) }
            ?: throw MalTokenRefreshFailed()
    }

    private fun decode(json: String): MALOAuth? =
        runCatching { trackerJson.decodeFromString(MALOAuth.serializer(), json) }.getOrNull()

    private fun encode(oauth: MALOAuth): String = trackerJson.encodeToString(MALOAuth.serializer(), oauth)
}

class MalTokenRefreshFailed : IOException("MAL: Failed to refresh account token")
class MalTokenExpired : IOException("MAL: Login has expired")
