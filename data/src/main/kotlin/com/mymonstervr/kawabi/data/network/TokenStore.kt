package com.mymonstervr.kawabi.data.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.tokenDataStore by preferencesDataStore(name = "kawabi_auth")

/**
 * Encrypts the session token with an Android Keystore-backed AES/GCM key before
 * persisting it via DataStore. [getToken] must stay synchronous (OkHttp's
 * [Interceptor][okhttp3.Interceptor] can't suspend), so the plaintext token is
 * cached in memory and only re-read from disk once at construction.
 */
class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")
    private val secretKey: SecretKey by lazy { getOrCreateKey() }

    @Volatile
    private var cachedToken: String? = runBlocking {
        context.tokenDataStore.data.first()[tokenKey]?.let(::decrypt)
    }

    private val _isLoggedIn = MutableStateFlow(cachedToken != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun getToken(): String? = cachedToken

    fun saveToken(token: String) {
        cachedToken = token
        _isLoggedIn.value = true
        runBlocking {
            context.tokenDataStore.edit { it[tokenKey] = encrypt(token) }
        }
    }

    fun clearToken() {
        cachedToken = null
        _isLoggedIn.value = false
        runBlocking {
            context.tokenDataStore.edit { it.remove(tokenKey) }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(encoded: String): String? {
        val parts = encoded.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private companion object {
        const val KEY_ALIAS = "kawabi_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
