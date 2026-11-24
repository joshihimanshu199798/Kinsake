package com.kinsake.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE childId = :childId ORDER BY dateMillis DESC, id DESC")
    fun observeChild(childId: String): Flow<List<Memory>>

    @Insert
    suspend fun insert(memory: Memory): Long

    @Insert
    suspend fun insertAll(memories: List<Memory>): List<Long>

    @Query("SELECT * FROM memories ORDER BY dateMillis DESC, id DESC")
    suspend fun getAll(): List<Memory>

    @Delete
    suspend fun delete(memory: Memory)

    @Query("SELECT * FROM memories WHERE kind = :kind ORDER BY dateMillis DESC")
    fun observeKind(kind: String): Flow<List<Memory>>
}
