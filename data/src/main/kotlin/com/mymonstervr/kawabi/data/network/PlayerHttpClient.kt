package com.mymonstervr.kawabi.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The OkHttp client the video player streams through -- deliberately NOT the shared
 * [createOkHttpClient] one, for two reasons:
 *
 * 1. That client's [AuthInterceptor] attaches the session Bearer token to every request it
 *    ever makes. A proxied stream (`/anime/stream/...`) genuinely requires the token on the
 *    playlist AND every segment, but a direct-CDN stream must never carry it -- the CDN is a
 *    third party, and stream/subtitle URLs come from extension-scraped pages, i.e. they are
 *    attacker-influenceable. So the token goes on only for an exact-host, https match on the
 *    backend itself: a prefix test would also pass for `sync.rasmushk.dk.evil.example`.
 * 2. A stalling segment must not look like a dead connection: an HLS segment sits on the
 *    same socket for as long as the CDN takes to hand it over, so the read timeout is far
 *    past the API clients' 10-20s, and a 401 here must not clear the session (mid-playback
 *    token expiry surfaces as a player error, not a silent logout).
 */
class PlayerHttpClient(private val tokenStore: TokenStore) {

    private val backendHost: String = BASE_URL.toHttpUrl().host

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val token = tokenStore.getToken()
            val isBackend = request.url.host.equals(backendHost, ignoreCase = true) && request.url.isHttps
            if (token != null && isBackend) {
                chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
            } else {
                chain.proceed(request)
            }
        }
        .build()
}
