package com.verimark.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CaseEntity::class, MarkerEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VeriMarkDatabase : RoomDatabase() {

    abstract fun caseDao(): CaseDao

    abstract fun markerDao(): MarkerDao

    companion object {
        @Volatile
        private var INSTANCE: VeriMarkDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN videoUri TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): VeriMarkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VeriMarkDatabase::class.java,
                    "verimark.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
