package com.verimark.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {

    @Insert
    suspend fun insert(marker: MarkerEntity): Long

    @Insert
    suspend fun insertAll(markers: List<MarkerEntity>): List<Long>

    @Delete
    suspend fun deleteMarker(marker: MarkerEntity)

    @Query("DELETE FROM markers WHERE caseId = :caseId")
    suspend fun clearAllMarkers(caseId: Long)

    @Query("SELECT COUNT(*) FROM markers WHERE caseId = :caseId")
    suspend fun markerCount(caseId: Long): Int

    @Query("SELECT videoUri FROM markers WHERE caseId = :caseId ORDER BY positionMs ASC LIMIT 1")
    suspend fun firstMarkerVideoUri(caseId: Long): String?

    @Query("SELECT * FROM markers WHERE caseId = :caseId ORDER BY positionMs ASC")
    fun markersForCase(caseId: Long): Flow<List<MarkerEntity>>
}
