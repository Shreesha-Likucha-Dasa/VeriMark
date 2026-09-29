package com.verimark.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A case joined with the count of its markers, used by the projects list. */
data class CaseWithMarkerCount(
    @Embedded val case: CaseEntity,
    @ColumnInfo(name = "markerCount") val markerCount: Int
)
