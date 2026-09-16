package com.mymonstervr.kawabi.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

enum class PageFitMode { FIT_WIDTH, FIT_HEIGHT, ORIGINAL }

enum class ThemePalette { NIGHT_SESSION, CATPPUCCIN_MOCHA }

// A page counts as "reached" once scroll position has covered at least this fraction of
// its height -- not only at a pixel-perfect bottom edge. 95 approximates the old fixed
// 48dp-slack behavior closely enough for a typical page/viewport size while being an
// actual user-adjustable setting instead of a hardcoded constant.
const val MARK_READ_THRESHOLD_MIN = 50
const val MARK_READ_THRESHOLD_MAX = 100
const val MARK_READ_THRESHOLD_DEFAULT = 95

// Directly the LazyVerticalGrid column count for Library/Search (GridCells.Fixed) --
// a raw slider value rather than a minSize-derived preset. GridCells.Adaptive(minSize=)
// was tried first, but it keeps adding columns as available width grows, so the same
// minSize meant very different column counts on phone vs tablet (a "Large" preset that
// gave 2 columns on phone gave 6+ on a real tablet) -- a direct column count sidesteps
// that entirely and works the same way at any screen size. Each device's DataStore is
// local/unsynced, so phone and tablet naturally keep independent preferences already.
const val LIBRARY_GRID_COLUMNS_MIN = 2
const val LIBRARY_GRID_COLUMNS_MAX = 8
const val LIBRARY_GRID_COLUMNS_DEFAULT = 3

// Fraction of an episode's duration that counts as "watched" for auto-marking. 0.85
// matches PLAN-anime.md's C2 default -- most anime episodes end with ~90s of outro/next-
// episode preview, so waiting for the literal end would rarely fire at all.
const val ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN = 0.5f
const val ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX = 1.0f
const val ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT = 0.85f

// Percent of PlayerView's default subtitle size -- stored as a whole-number percent
// (matching the other percent-based settings above) rather than a raw fraction, so the
// slider and its label both work in the same units.
const val SUBTITLE_TEXT_SIZE_MIN = 50
const val SUBTITLE_TEXT_SIZE_MAX = 200
const val SUBTITLE_TEXT_SIZE_DEFAULT = 100

enum class SubtitleBackgroundStyle { OUTLINE, BOX }

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
    private val libraryGridColumnsKey = intPreferencesKey("library_grid_columns")
    private val hideReadChaptersKey = booleanPreferencesKey("hide_read_chapters")
    private val chapterSortAscendingKey = booleanPreferencesKey("chapter_sort_ascending")
    private val pageFitModeKey = stringPreferencesKey("page_fit_mode")
    private val markReadThresholdKey = intPreferencesKey("mark_read_threshold")
    private val themePaletteKey = stringPreferencesKey("theme_palette")
    private val amoledBlackKey = booleanPreferencesKey("amoled_black")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val animeAutoMarkWatchedThresholdKey = floatPreferencesKey("anime_auto_mark_watched_threshold")
    private val animePreferredQualityKey = stringPreferencesKey("anime_preferred_quality")
    private val animeAutoSkipIntroKey = booleanPreferencesKey("anime_auto_skip_intro")
    private val animeAutoImportEnabledKey = booleanPreferencesKey("anime_auto_import_enabled")
    private val trackerLastVerifiedAtKey = longPreferencesKey("tracker_last_verified_at")
    private val subtitleTextSizeKey = intPreferencesKey("subtitle_text_size")
    private val subtitleBackgroundStyleKey = stringPreferencesKey("subtitle_background_style")

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

    val libraryGridColumns: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[libraryGridColumnsKey] ?: LIBRARY_GRID_COLUMNS_DEFAULT)
            .coerceIn(LIBRARY_GRID_COLUMNS_MIN, LIBRARY_GRID_COLUMNS_MAX)
    }

    suspend fun setLibraryGridColumns(count: Int) {
        context.settingsDataStore.edit { it[libraryGridColumnsKey] = count.coerceIn(LIBRARY_GRID_COLUMNS_MIN, LIBRARY_GRID_COLUMNS_MAX) }
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

    // Chapter-list reading habits -- global, not per-manga, since they're about how you
    // like to browse a list rather than a fact about any one series.
    val hideReadChapters: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[hideReadChaptersKey] ?: false
    }

    suspend fun setHideReadChapters(enabled: Boolean) {
        context.settingsDataStore.edit { it[hideReadChaptersKey] = enabled }
    }

    val chapterSortAscending: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[chapterSortAscendingKey] ?: false
    }

    suspend fun setChapterSortAscending(ascending: Boolean) {
        context.settingsDataStore.edit { it[chapterSortAscendingKey] = ascending }
    }

    // Per-manga preferred scanlator (e.g. "official" vs "unofficial" on MangaFire) --
    // null means "show both versions". Device-local by design, same as everything else
    // in this class; a manga with only one scanlator per chapter number never reads this.
    //
    // Keyed on the manga's URL, not its local DB row id -- unlike the reader (which only
    // ever opens a manga already in the local library), the detail screen's version
    // picker must also work for a manga the user is merely browsing and hasn't favorited
    // yet, which has no local row id at all.
    fun preferredScanlator(mangaUrl: String): Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[preferredScanlatorKey(mangaUrl)]
    }

    suspend fun setPreferredScanlator(mangaUrl: String, scanlator: String?) {
        context.settingsDataStore.edit { prefs ->
            if (scanlator == null) prefs.remove(preferredScanlatorKey(mangaUrl))
            else prefs[preferredScanlatorKey(mangaUrl)] = scanlator
        }
    }

    private fun preferredScanlatorKey(mangaUrl: String) = stringPreferencesKey("preferred_scanlator_$mangaUrl")

    val pageFitMode: Flow<PageFitMode> = context.settingsDataStore.data.map { prefs ->
        prefs[pageFitModeKey]?.let { runCatching { PageFitMode.valueOf(it) }.getOrNull() } ?: PageFitMode.FIT_WIDTH
    }

    suspend fun setPageFitMode(mode: PageFitMode) {
        context.settingsDataStore.edit { it[pageFitModeKey] = mode.name }
    }

    val markReadThreshold: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[markReadThresholdKey] ?: MARK_READ_THRESHOLD_DEFAULT)
            .coerceIn(MARK_READ_THRESHOLD_MIN, MARK_READ_THRESHOLD_MAX)
    }

    suspend fun setMarkReadThreshold(percent: Int) {
        context.settingsDataStore.edit { it[markReadThresholdKey] = percent.coerceIn(MARK_READ_THRESHOLD_MIN, MARK_READ_THRESHOLD_MAX) }
    }

    // Per-manga reading-direction override -- null means "use the global default".
    // URL-keyed like preferredScanlator, for the same reason (must resolve for a manga
    // not yet favorited/in the local library).
    fun readingDirectionOverride(mangaUrl: String): Flow<ReadingDirection?> = context.settingsDataStore.data.map { prefs ->
        prefs[readingDirectionOverrideKey(mangaUrl)]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
    }

    suspend fun setReadingDirectionOverride(mangaUrl: String, direction: ReadingDirection?) {
        context.settingsDataStore.edit { prefs ->
            if (direction == null) prefs.remove(readingDirectionOverrideKey(mangaUrl))
            else prefs[readingDirectionOverrideKey(mangaUrl)] = direction.name
        }
    }

    private fun readingDirectionOverrideKey(mangaUrl: String) = stringPreferencesKey("reading_direction_override_$mangaUrl")

    val themePalette: Flow<ThemePalette> = context.settingsDataStore.data.map { prefs ->
        prefs[themePaletteKey]?.let { runCatching { ThemePalette.valueOf(it) }.getOrNull() } ?: ThemePalette.NIGHT_SESSION
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        context.settingsDataStore.edit { it[themePaletteKey] = palette.name }
    }

    // Forces the background back to pure black regardless of the selected palette --
    // meaningful even for Night Session (whose background already IS black, so this is a
    // no-op there) once a non-black palette like Catppuccin Mocha exists to override.
    val amoledBlack: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[amoledBlackKey] ?: false
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.settingsDataStore.edit { it[amoledBlackKey] = enabled }
    }

    // Material You dynamic color (Android 12+) -- overrides the selected palette entirely
    // when both this is on and the OS actually supports it (KawabiTheme checks SDK_INT).
    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicColorKey] = enabled }
    }

    // Player preferences (PLAN-anime.md C2). Stored and settable now, but nothing reads
    // them yet -- the player itself is the next slice, and having the keys already in
    // place means a stored choice survives from before it lands.
    val animeAutoMarkWatchedThreshold: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        (prefs[animeAutoMarkWatchedThresholdKey] ?: ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT)
            .coerceIn(ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN, ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX)
    }

    suspend fun setAnimeAutoMarkWatchedThreshold(fraction: Float) {
        context.settingsDataStore.edit {
            it[animeAutoMarkWatchedThresholdKey] =
                fraction.coerceIn(ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN, ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX)
        }
    }

    /** Empty string means "best available" -- the player picks the highest resolution. */
    val animePreferredQuality: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[animePreferredQualityKey].orEmpty()
    }

    suspend fun setAnimePreferredQuality(quality: String) {
        context.settingsDataStore.edit { it[animePreferredQualityKey] = quality }
    }

    val animeAutoSkipIntro: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[animeAutoSkipIntroKey] ?: false
    }

    suspend fun setAnimeAutoSkipIntro(enabled: Boolean) {
        context.settingsDataStore.edit { it[animeAutoSkipIntroKey] = enabled }
    }

    // Gates AutoImportAnimeFromTrackers entirely -- off means it never runs, even
    // right after a fresh tracker connect (see that class for the per-tracker cooldown).
    val animeAutoImportEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[animeAutoImportEnabledKey] ?: true
    }

    suspend fun setAnimeAutoImportEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[animeAutoImportEnabledKey] = enabled }
    }

    suspend fun animeLastAutoImportAt(trackerId: String): Long =
        context.settingsDataStore.data.first()[animeLastAutoImportAtKey(trackerId)] ?: 0L

    suspend fun setAnimeLastAutoImportAt(trackerId: String, timeMillis: Long) {
        context.settingsDataStore.edit { it[animeLastAutoImportAtKey(trackerId)] = timeMillis }
    }

    private fun animeLastAutoImportAtKey(trackerId: String) = longPreferencesKey("anime_last_auto_import_at_$trackerId")

    // Throttles the active-verify tracker/status call (upstream-checking, not just cached)
    // to at most once every 6h -- TrackerManager.refreshIfVerifyDue() is the only caller
    // that consults this; a screen-open refresh always verifies regardless.
    suspend fun isTrackerVerifyDue(): Boolean {
        val last = context.settingsDataStore.data.first()[trackerLastVerifiedAtKey] ?: 0L
        return System.currentTimeMillis() - last > TimeUnit.HOURS.toMillis(6)
    }

    suspend fun markTrackerVerified() {
        context.settingsDataStore.edit { it[trackerLastVerifiedAtKey] = System.currentTimeMillis() }
    }

    val subtitleTextSize: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[subtitleTextSizeKey] ?: SUBTITLE_TEXT_SIZE_DEFAULT).coerceIn(SUBTITLE_TEXT_SIZE_MIN, SUBTITLE_TEXT_SIZE_MAX)
    }

    suspend fun setSubtitleTextSize(percent: Int) {
        context.settingsDataStore.edit { it[subtitleTextSizeKey] = percent.coerceIn(SUBTITLE_TEXT_SIZE_MIN, SUBTITLE_TEXT_SIZE_MAX) }
    }

    val subtitleBackgroundStyle: Flow<SubtitleBackgroundStyle> = context.settingsDataStore.data.map { prefs ->
        prefs[subtitleBackgroundStyleKey]?.let { runCatching { SubtitleBackgroundStyle.valueOf(it) }.getOrNull() }
            ?: SubtitleBackgroundStyle.OUTLINE
    }

    suspend fun setSubtitleBackgroundStyle(style: SubtitleBackgroundStyle) {
        context.settingsDataStore.edit { it[subtitleBackgroundStyleKey] = style.name }
    }
}
