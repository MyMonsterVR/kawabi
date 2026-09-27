package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairingCreateRequest(
    val device_name: String,
)

@Serializable
data class PairingCreateResponse(
    val code: String,
    val display_code: String,
    val poll_secret: String,
    val qr_payload: String,
    val expires_at: Long,
    val poll_interval_ms: Long,
)

@Serializable
data class PairingPollRequest(
    val poll_secret: String,
)

@Serializable
data class PairingPollResponse(
    val status: String,
    val token: String? = null,
)

@Serializable
data class PairingApproveRequest(
    val code: String,
)

@Serializable
data class PairingApproveResponse(
    val device_name: String,
)
