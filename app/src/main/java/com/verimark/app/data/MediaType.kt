package com.verimark.app.data

/** The type of media attached to a case. */
enum class MediaType {
    VIDEO,
    AUDIO;

    companion object {
        /** Safely maps a stored value back to a [MediaType], defaulting to [VIDEO]. */
        fun fromStorage(value: String): MediaType =
            entries.firstOrNull { it.name == value } ?: VIDEO
    }
}
