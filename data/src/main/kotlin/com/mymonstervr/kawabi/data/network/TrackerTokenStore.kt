package com.mymonstervr.kawabi.data.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.trackerDataStore by preferencesDataStore(name = "kawabi_tracker_auth")

/**
 * Per-tracker (MAL, Kitsu, ...) connection cache -- display name only, no
 * credentials or OAuth tokens (those are held by the backend now). Populated
 * from [TrackerManager]'s [TrackerApi.status] refresh and kept in sync
 * on-device by connect/logout so [loggedInTrackerIds] updates immediately.
 */
class TrackerTokenStore(private val context: Context) {

    private val initialPrefs = runBlocking { context.trackerDataStore.data.first() }

    @Volatile
    private var profiles: Map<String, String> =
        SUPPORTED_TRACKERS.mapNotNull { id -> initialPrefs[profileKey(id)]?.let { id to it } }.toMap()

    private val _loggedInTrackerIds = MutableStateFlow(profiles.keys)
    val loggedInTrackerIds: StateFlow<Set<String>> = _loggedInTrackerIds.asStateFlow()

    fun getProfile(trackerId: String): String? = profiles[trackerId]

    fun saveProfile(trackerId: String, userName: String) {
        profiles = profiles + (trackerId to userName)
        _loggedInTrackerIds.value = profiles.keys
        runBlocking { context.trackerDataStore.edit { it[profileKey(trackerId)] = userName } }
    }

    fun clearProfile(trackerId: String) {
        profiles = profiles - trackerId
        _loggedInTrackerIds.value = profiles.keys
        runBlocking { context.trackerDataStore.edit { it.remove(profileKey(trackerId)) } }
    }

    fun replaceAll(statuses: List<TrackerStatus>) {
        profiles = statuses.associate { it.tracker to it.userName }
        _loggedInTrackerIds.value = profiles.keys
        runBlocking {
            context.trackerDataStore.edit { prefs ->
                SUPPORTED_TRACKERS.forEach { prefs.remove(profileKey(it)) }
                statuses.forEach { prefs[profileKey(it.tracker)] = it.userName }
            }
        }
    }

    private fun profileKey(trackerId: String) = stringPreferencesKey("profile_$trackerId")

    companion object {
        const val TRACKER_MAL = "mal"
        const val TRACKER_KITSU = "kitsu"
        private val SUPPORTED_TRACKERS = listOf(TRACKER_MAL, TRACKER_KITSU)
    }
}
