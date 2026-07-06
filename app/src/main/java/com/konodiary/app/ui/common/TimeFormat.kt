package com.konodiary.app.ui.common

import java.util.Locale

/** Formats a millisecond duration as h:mm:ss or mm:ss. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** Parses a "mm:ss" or "h:mm:ss" string into milliseconds, or null if invalid. */
fun parseDuration(text: String): Long? {
    val parts = text.trim().split(":")
    if (parts.isEmpty() || parts.size > 3) return null
    val nums = parts.map { it.toIntOrNull() ?: return null }
    val seconds = when (nums.size) {
        1 -> nums[0]
        2 -> nums[0] * 60 + nums[1]
        3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
        else -> return null
    }
    return seconds.toLong() * 1000
}
