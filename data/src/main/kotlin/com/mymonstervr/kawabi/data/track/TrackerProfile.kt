package com.mymonstervr.kawabi.data.track

import kotlinx.serialization.Serializable

/** Public display info for a connected tracker account -- not a credential, stored unencrypted. */
@Serializable
data class TrackerProfile(val userName: String, val userId: String? = null) {
    companion object {
        fun decode(json: String): TrackerProfile? =
            runCatching { trackerJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}
