package com.verimark.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "markers",
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("caseId")]
)
data class MarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val caseId: Long,
    val videoUri: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long
)
