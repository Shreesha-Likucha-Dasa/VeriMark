package com.verimark.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {

    @Insert
    suspend fun insert(caseEntity: CaseEntity): Long

    @Query("SELECT * FROM cases WHERE id = :caseId")
    suspend fun getCase(caseId: Long): CaseEntity?

    @Query("SELECT * FROM cases ORDER BY date DESC")
    fun allCases(): Flow<List<CaseEntity>>

    @Query("SELECT cases.*, (SELECT COUNT(*) FROM markers WHERE markers.caseId = cases.id) AS markerCount FROM cases ORDER BY date DESC")
    fun casesWithMarkerCount(): Flow<List<CaseWithMarkerCount>>

    @Delete
    suspend fun deleteCase(caseEntity: CaseEntity)

    @Query("SELECT COUNT(*) FROM cases")
    suspend fun caseCount(): Int

    @Query("UPDATE cases SET videoUri = :videoUri, mediaType = :mediaType WHERE id = :caseId")
    suspend fun updateMedia(caseId: Long, videoUri: String, mediaType: MediaType)
}
