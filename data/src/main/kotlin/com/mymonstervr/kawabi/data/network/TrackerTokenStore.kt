package com.mymonstervr.kawabi.data.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
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

private val Context.trackerDataStore by preferencesDataStore(name = "kawabi_tracker_auth")

/**
 * Per-tracker (MAL, Kitsu, ...) session storage -- same Android Keystore-backed
 * AES/GCM scheme as [TokenStore], but keyed by tracker id since there's more
 * than one external account to hold. Each tracker's interceptor owns its own
 * opaque OAuth JSON blob; this store only encrypts/persists it. [getSession]/
 * [isAuthExpired] must stay synchronous so an OkHttp [Interceptor][okhttp3.Interceptor]
 * can read them without suspending, same constraint as [TokenStore].
 *
 * Display name/user id are stored separately, unencrypted (public info, not a
 * credential) -- see [getProfile]/[saveProfile].
 */
class TrackerTokenStore(private val context: Context) {

    private val secretKey: SecretKey by lazy { getOrCreateKey() }

    private val initialPrefs = runBlocking { context.trackerDataStore.data.first() }

    @Volatile
    private var sessions: Map<String, String> = SUPPORTED_TRACKERS.mapNotNull { id ->
        initialPrefs[sessionKey(id)]?.let(::decrypt)?.let { id to it }
    }.toMap()

    @Volatile
    private var expired: Set<String> = SUPPORTED_TRACKERS.filter { initialPrefs[expiredKey(it)] == true }.toSet()

    @Volatile
    private var profiles: Map<String, String> =
        SUPPORTED_TRACKERS.mapNotNull { id -> initialPrefs[profileKey(id)]?.let { id to it } }.toMap()

    private val _loggedInTrackerIds = MutableStateFlow(sessions.keys)
    val loggedInTrackerIds: StateFlow<Set<String>> = _loggedInTrackerIds.asStateFlow()

    fun getSession(trackerId: String): String? = sessions[trackerId]

    fun saveSession(trackerId: String, json: String) {
        sessions = sessions + (trackerId to json)
        expired = expired - trackerId
        _loggedInTrackerIds.value = sessions.keys
        runBlocking {
            context.trackerDataStore.edit {
                it[sessionKey(trackerId)] = encrypt(json)
                it.remove(expiredKey(trackerId))
            }
        }
    }

    fun clearSession(trackerId: String) {
        sessions = sessions - trackerId
        expired = expired - trackerId
        profiles = profiles - trackerId
        _loggedInTrackerIds.value = sessions.keys
        runBlocking {
            context.trackerDataStore.edit {
                it.remove(sessionKey(trackerId))
                it.remove(expiredKey(trackerId))
                it.remove(profileKey(trackerId))
            }
        }
    }

    fun isAuthExpired(trackerId: String): Boolean = trackerId in expired

    fun setAuthExpired(trackerId: String) {
        expired = expired + trackerId
        runBlocking { context.trackerDataStore.edit { it[expiredKey(trackerId)] = true } }
    }

    fun getProfile(trackerId: String): String? = profiles[trackerId]

    fun saveProfile(trackerId: String, json: String) {
        profiles = profiles + (trackerId to json)
        runBlocking { context.trackerDataStore.edit { it[profileKey(trackerId)] = json } }
    }

    private fun sessionKey(trackerId: String) = stringPreferencesKey("session_$trackerId")
    private fun expiredKey(trackerId: String) = booleanPreferencesKey("expired_$trackerId")
    private fun profileKey(trackerId: String) = stringPreferencesKey("profile_$trackerId")

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
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        const val TRACKER_MAL = "mal"
        const val TRACKER_KITSU = "kitsu"
        private val SUPPORTED_TRACKERS = listOf(TRACKER_MAL, TRACKER_KITSU)

        private const val KEY_ALIAS = "kawabi_tracker_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
