package com.verimark.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats a millisecond position as MM:SS. */
fun formatMs(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/** Formats an epoch-millis timestamp as a readable date (yyyy-MM-dd). */
fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))
