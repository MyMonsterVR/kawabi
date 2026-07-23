package com.mymonstervr.kawabi.data.track.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KitsuOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String?,
) {
    fun isExpired(): Boolean = (System.currentTimeMillis() / 1000) > (createdAt + expiresIn - 3600)
}

@Serializable
data class KitsuCurrentUserResult(val data: List<KitsuUser>)

@Serializable
data class KitsuUser(val id: String, val attributes: KitsuUserAttributes)

@Serializable
data class KitsuUserAttributes(val name: String)
