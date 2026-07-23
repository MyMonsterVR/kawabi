package com.mymonstervr.kawabi.app.update

import com.mymonstervr.kawabi.BuildConfig
import com.mymonstervr.kawabi.data.network.AppReleaseApi
import com.mymonstervr.kawabi.data.settings.AppPreferences

data class AppUpdateInfo(val version: String, val info: String, val downloadUrl: String)

/**
 * Compares the running build's commit count (BuildConfig.COMMIT_COUNT, baked in at
 * CI build time) against the manifest CI publishes on every push. Commit count, not
 * the human-edited `version` string, drives "is this newer" -- always changes on a
 * fresh push, so a build never gets stuck failing to detect an update just because
 * versionName wasn't bumped.
 */
class AppUpdateChecker(
    private val releaseApi: AppReleaseApi,
    private val appPreferences: AppPreferences,
) {
    suspend fun check(forceCheck: Boolean = false): AppUpdateInfo? {
        if (!forceCheck && !appPreferences.isUpdateCheckDue()) return null

        val release = releaseApi.latest() ?: return null
        appPreferences.markUpdateChecked()

        if (release.commitCount <= BuildConfig.COMMIT_COUNT) return null
        return AppUpdateInfo(version = release.version, info = release.info, downloadUrl = release.downloadUrl)
    }
}
