package com.verimark.app.data

import androidx.room.TypeConverter

/** Room converter that persists [MediaType] as its enum name (TEXT). */
class MediaTypeConverter {

    @TypeConverter
    fun toStorage(value: MediaType): String = value.name

    @TypeConverter
    fun fromStorage(value: String): MediaType = MediaType.fromStorage(value)
}
