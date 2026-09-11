package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.data.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

val networkJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

const val BASE_URL: String = BuildConfig.BASE_URL

/** Site-key prefix every anime source carries in the backend contract ("anime:<engineSourceId>"). */
const val ANIME_SOURCE_PREFIX: String = "anime:"

/** `/pages` returns `proxied_image_url` as a path relative to [BASE_URL], not an absolute URL. */
fun resolveImageUrl(proxiedImageUrl: String): String =
    if (proxiedImageUrl.startsWith("http")) proxiedImageUrl else "$BASE_URL$proxiedImageUrl"

/**
 * `cover_url` doesn't behave like a plain image URL -- port of kawabi-web's
 * `proxiedCover()` (`src/lib/covers.ts`). Suwayomi-served manga (e.g. anything routed
 * through the MangaFire/Weeb Central/etc. extension bridge) return it as a server-relative
 * REST path (`/api/v1/manga/{id}/thumbnail`), not a hittable URL at all -- must go through
 * the backend's `/image` proxy with `source=suwayomi`. AsuraScans covers are absolute but
 * reject hotlinking without the Referer the `/image` proxy supplies, so those also need
 * routing through it. Everything else (MAL/Kitsu CDNs, etc.) is hotlink-friendly and loads
 * directly.
 */
fun resolveCoverUrl(url: String?, source: String? = null): String? {
    if (url.isNullOrBlank()) return url
    // Anime covers always go through the proxy: the engine's sources hand back CDN urls
    // that need the extension's own Referer/UA, which only the backend's /image handler
    // (delegating to the engine's /image) has. `source` is the contract's "anime:<id>"
    // site key, passed straight through as the proxy's source parameter.
    if (source != null && source.startsWith(ANIME_SOURCE_PREFIX)) return proxiedImageUrl(source, url)
    if (url.startsWith("/api/")) return proxiedImageUrl("suwayomi", url)
    val host = runCatching { url.toHttpUrl().host }.getOrNull() ?: return url
    if (host.endsWith("asurascans.com") || host.endsWith("asuracomic.net")) {
        return proxiedImageUrl("asurascans", url)
    }
    return url
}

// Every non-2xx handler response body is {"error": "..."} (internal/handler/error.go's
// writeJSONError) -- without this, callers only ever saw a generic HTTP-code-and-URL
// message regardless of which source actually failed or why. Plain-language, no URL in
// the fallback either: this text must never look like a raw diagnostic, since a client
// running code from before this function existed threw an almost-identical-looking
// "Request failed: HTTP <code> for <url>" string -- keeping this one visibly different in
// wording (not just missing a URL) makes a stale build immediately obvious if ever seen
// again, instead of looking like this parsing still doesn't work.
fun errorMessageFor(response: Response): String {
    val fallback = "Couldn't reach the server (HTTP ${response.code})"
    val body = runCatching { response.body.string() }.getOrNull().takeUnless { it.isNullOrBlank() } ?: return fallback
    return runCatching { networkJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }
        .getOrNull() ?: fallback
}

private fun proxiedImageUrl(source: String, src: String): String =
    "$BASE_URL/image".toHttpUrl().newBuilder()
        .addQueryParameter("source", source)
        .addQueryParameter("src", src)
        .build()
        .toString()

fun createOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

// `/image` can legitimately take up to ~60s to answer (mihon-sync-server's fetchValidImage
// retries a slow source CDN up to 3x, each with its own ~20s upstream timeout, before it
// ever starts writing a response) -- confirmed live via server logs showing routine
// single-attempt fetches sitting right around 10s against asurascans' CDN. Coil's default
// ImageLoader falls back to a bare `OkHttpClient()` with stock 10s connect/read/write
// timeouts, which raced that ~10s fetch time almost exactly -- confirmed as the cause of
// intermittent "images not loading", not a server bug: the client gave up right as the
// server was about to deliver, then Coil/OkHttp retried into the same race again.
fun createImageOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()
