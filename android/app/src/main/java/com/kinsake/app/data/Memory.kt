package com.kinsake.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val note: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val childId: String = "default-child"
)
