package com.mymonstervr.kawabi.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.settingsDataStore by preferencesDataStore(name = "kawabi_settings")

enum class ReadingDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT, VERTICAL }

/**
 * Tier 1 Settings (PLAN.md step 8) that actually affect app behavior today. Global
 * defaults only -- per-series override (reading direction) isn't built yet, same for
 * anything needing backend work beyond /sources (accent color, preferred-source sync,
 * tracking services all deferred, see PLAN.md).
 */
class AppPreferences(private val context: Context) {

    private val readingDirectionKey = stringPreferencesKey("reading_direction")
    private val markReadOnScrollKey = booleanPreferencesKey("mark_read_on_scroll")
    private val keepScreenAwakeKey = booleanPreferencesKey("keep_screen_awake")
    private val accentIndexKey = intPreferencesKey("accent_index")
    private val lastUpdateCheckKey = longPreferencesKey("last_update_check")

    // Index into NightSession.Accents -- local-only styling, no backend concept of it
    // (PLAN.md's Settings step explicitly scoped this as pure local theming).
    val accentIndex: Flow<Int> = context.settingsDataStore.data.map { prefs -> prefs[accentIndexKey] ?: 0 }

    suspend fun setAccentIndex(index: Int) {
        context.settingsDataStore.edit { it[accentIndexKey] = index }
    }

    val readingDirection: Flow<ReadingDirection> = context.settingsDataStore.data.map { prefs ->
        prefs[readingDirectionKey]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
            ?: ReadingDirection.VERTICAL
    }

    // Whether reaching the last page while scrolling auto-marks a chapter read. Off still
    // tracks lastPageRead as normal -- only the `read` flag itself is gated, keeping
    // "read progress" and "marked read" as distinct concepts.
    val markReadOnScroll: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[markReadOnScrollKey] ?: true
    }

    val keepScreenAwake: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[keepScreenAwakeKey] ?: false
    }

    suspend fun setReadingDirection(direction: ReadingDirection) {
        context.settingsDataStore.edit { it[readingDirectionKey] = direction.name }
    }

    suspend fun setMarkReadOnScroll(enabled: Boolean) {
        context.settingsDataStore.edit { it[markReadOnScrollKey] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        context.settingsDataStore.edit { it[keepScreenAwakeKey] = enabled }
    }

    // Update-check throttle -- mirrors the old fork's "at most once every 3 days"
    // rule so a silent background check on every app launch doesn't hammer the
    // manifest endpoint. forceCheck (Settings' manual button) bypasses this.
    suspend fun isUpdateCheckDue(): Boolean {
        val last = context.settingsDataStore.data.first()[lastUpdateCheckKey] ?: 0L
        return System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(3)
    }

    suspend fun markUpdateChecked() {
        context.settingsDataStore.edit { it[lastUpdateCheckKey] = System.currentTimeMillis() }
    }
}
