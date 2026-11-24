package com.kinsake.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Memory::class], version = 2, exportSchema = false)
abstract class KinsakeDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE memories ADD COLUMN childId TEXT NOT NULL DEFAULT 'default-child'")
            }
        }
        @Volatile private var INSTANCE: KinsakeDatabase? = null
        fun get(context: Context): KinsakeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KinsakeDatabase::class.java,
                    "kinsake-local.db"
                    .addMigrations(MIGRATION_1_2)
                ).build().also { INSTANCE = it }
            }
    }
}
