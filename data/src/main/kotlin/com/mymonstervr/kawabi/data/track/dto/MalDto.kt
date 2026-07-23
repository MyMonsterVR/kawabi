package com.mymonstervr.kawabi.data.track.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALOAuth(
    @SerialName("token_type") val tokenType: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    // Assumes expired a minute earlier, to leave room for request latency.
    fun isExpired(): Boolean = createdAt + (expiresIn - 60) < System.currentTimeMillis() / 1000
}

@Serializable
data class MALUser(val name: String)
