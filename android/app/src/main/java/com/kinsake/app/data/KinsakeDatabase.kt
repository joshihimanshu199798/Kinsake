package com.kinsake.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Memory::class], version = 1, exportSchema = false)
abstract class KinsakeDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var INSTANCE: KinsakeDatabase? = null
        fun get(context: Context): KinsakeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KinsakeDatabase::class.java,
                    "kinsake-local.db"
                ).build().also { INSTANCE = it }
            }
    }
}
