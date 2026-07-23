package com.mymonstervr.kawabi.data.track

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

// Separate from network.networkJson: encodeDefaults = true so MALOAuth's
// createdAt (only populated via its default value when MAL's response omits
// the field) still round-trips when we re-encode the session for our own
// on-device persistence.
val trackerJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

// Shared plain client for MAL/Kitsu's own APIs, not the app's backend-authenticated
// one (network.createOkHttpClient's AuthInterceptor is for kawabi-server, unrelated
// to and would be wrong to attach on requests to MAL's/Kitsu's APIs). One shared
// Dispatcher/ConnectionPool for both trackers instead of each building its own.
val trackerHttpClient: OkHttpClient = OkHttpClient()
