package com.mymonstervr.kawabi.data.update

import com.mymonstervr.kawabi.data.network.AppReleaseApi
import com.mymonstervr.kawabi.data.settings.AppPreferences

data class AppUpdateInfo(val version: String, val info: String, val downloadUrl: String)

/**
 * Compares the running build's commit count against the manifest CI publishes on every push.
 * Commit count, not the human-edited `version` string, drives "is this newer" -- always
 * changes on a fresh push, so a build never gets stuck failing to detect an update just
 * because versionName wasn't bumped. [runningCommitCount] is the caller's own
 * BuildConfig.COMMIT_COUNT -- each app module generates its own BuildConfig class (different
 * package per applicationId), so it can't be read directly from here.
 */
class AppUpdateChecker(
    private val releaseApi: AppReleaseApi,
    private val appPreferences: AppPreferences,
    private val runningCommitCount: Int,
) {
    suspend fun check(forceCheck: Boolean = false): AppUpdateInfo? {
        if (!forceCheck && !appPreferences.isUpdateCheckDue()) return null

        val release = releaseApi.latest() ?: return null
        appPreferences.markUpdateChecked()

        if (release.commitCount <= runningCommitCount) return null
        return AppUpdateInfo(version = release.version, info = release.info, downloadUrl = release.downloadUrl)
    }
}
