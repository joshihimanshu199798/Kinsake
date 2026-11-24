package com.kinsake.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MemoryRepository(context: Context) {
    private val app = context.applicationContext
    private val dao = KinsakeDatabase.get(app).memoryDao()
    val memories: Flow<List<Memory>> = dao.observeAll()

    suspend fun importPhoto(uri: Uri) = withContext(Dispatchers.IO) {
        val directory = File(app.filesDir, "memories/photos").apply { mkdirs() }
        val destination = File(directory, "photo_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
        app.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        } ?: error("Could not read selected photo")
        dao.insert(Memory(kind = "photo", title = destination.nameWithoutExtension, filePath = destination.absolutePath))
    }

    suspend fun addMilestone(title: String, note: String) = withContext(Dispatchers.IO) {
        dao.insert(Memory(kind = "milestone", title = title.trim(), note = note.trim()))
    }

    suspend fun addVoice(file: File, title: String) = withContext(Dispatchers.IO) {
        dao.insert(Memory(kind = "voice", title = title, filePath = file.absolutePath))
    }

    suspend fun delete(memory: Memory) = withContext(Dispatchers.IO) {
        memory.filePath?.let { File(it).delete() }
        dao.delete(memory)
    }

    fun storageUsedBytes(): Long {
        val root = File(app.filesDir, "memories")
        return if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }
}
