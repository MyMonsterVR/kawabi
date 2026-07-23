package com.mymonstervr.kawabi.domain.util

// Must match kawabi-server's internal/db/library.go NativeRemoteID bit-for-bit:
// FNV-1a 64-bit of the UTF-8 manga URL, sign bit cleared so it's always positive.
private val FNV_OFFSET_BASIS = 0xcbf29ce484222325UL.toLong()
private const val FNV_PRIME = 0x100000001b3L

fun nativeRemoteId(mangaKey: String): Long {
    var hash = FNV_OFFSET_BASIS
    for (byte in mangaKey.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= FNV_PRIME
    }
    return hash and 0x7FFFFFFFFFFFFFFFL
}
