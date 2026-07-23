package com.mymonstervr.kawabi.data.track

import java.security.SecureRandom
import java.util.Base64

/** PKCE code verifier generation, needed for MAL's OAuth login flow. */
object PkceUtil {
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(50)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
