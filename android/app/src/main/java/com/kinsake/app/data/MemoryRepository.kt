package com.kinsake.app.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MemoryRepository(context: Context) {
    private val app = context.applicationContext
    private val dao = KinsakeDatabase.get(app).memoryDao()
    fun observeMemories(childId: String): Flow<List<Memory>> = dao.observeChild(childId)

    suspend fun importPhoto(uri: Uri, childId: String) = withContext(Dispatchers.IO) {
        val directory = File(app.filesDir, "memories/photos").apply { mkdirs() }
        val destination = File(directory, "photo_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: error("Could not read selected photo")
            val capturedAt = runCatching { ExifInterface(destination.absolutePath).dateTimeOriginal }.getOrNull()?.takeIf { it > 0L } ?: System.currentTimeMillis()
            dao.insert(Memory(kind = "photo", title = destination.nameWithoutExtension, dateMillis = capturedAt, filePath = destination.absolutePath, childId = childId))
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    suspend fun addMilestone(title: String, note: String, childId: String) = withContext(Dispatchers.IO) {
        dao.insert(Memory(kind = "milestone", title = title.trim(), note = note.trim(), childId = childId))
    }

    suspend fun addVoice(file: File, title: String, childId: String) = withContext(Dispatchers.IO) {
        dao.insert(Memory(kind = "voice", title = title, filePath = file.absolutePath, childId = childId))
    }

    suspend fun delete(memory: Memory) = withContext(Dispatchers.IO) {
        memory.filePath?.let { File(it).delete() }
        dao.delete(memory)
    }

    /** Writes a portable ZIP archive containing all memory metadata, private media and the child birth date. */
    suspend fun exportBackup(output: OutputStream, profiles: JSONArray) = withContext(Dispatchers.IO) {
        val records = dao.getAll()
        ZipOutputStream(output.buffered()).use { zip ->
            val mediaEntries = JSONArray()
            val manifest = JSONObject()
                .put("format", "kinsake-android-backup")
                .put("version", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("profiles", profiles)
            records.forEachIndexed { index, memory ->
                val item = JSONObject()
                    .put("kind", memory.kind)
                    .put("title", memory.title)
                    .put("note", memory.note)
                    .put("dateMillis", memory.dateMillis)
                    .put("childId", memory.childId)
                val source = memory.filePath?.let(::File)
                if (source != null && source.isFile) {
                    val ext = source.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
                    val archiveName = "media/${index}.${ext}"
                    item.put("mediaName", archiveName)
                    zip.putNextEntry(ZipEntry(archiveName))
                    source.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                mediaEntries.put(item)
            }
            manifest.put("memories", mediaEntries)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.finish()
        }
    }

    /**
     * Validates the complete archive before changing the database. Restore merges memories;
     * it never clears existing records. Extracted files are staged outside the live media folder.
     */
    suspend fun restoreBackup(input: InputStream): JSONArray? = withContext(Dispatchers.IO) {
        val staging = File(app.cacheDir, "kinsake-restore-${UUID.randomUUID()}").apply { mkdirs() }
        val installedFiles = mutableListOf<File>()
        try {
            var totalBytes = 0L
            var manifestBytes: ByteArray? = null
            val seen = mutableSetOf<String>()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) throw IllegalArgumentException("Folders are not allowed in this backup")
                    val name = entry.name
                    if (name != "manifest.json" && !name.matches(Regex("media/[A-Za-z0-9_.-]{1,80}"))) {
                        throw IllegalArgumentException("Backup contains an unsupported file path")
                    }
                    if (!seen.add(name)) throw IllegalArgumentException("Backup contains duplicate entries")
                    val destination = if (name == "manifest.json") null else File(staging, name.substringAfter("media/"))
                    if (destination != null) destination.parentFile?.mkdirs()
                    val buffer = ByteArray(16 * 1024)
                    val captured = java.io.ByteArrayOutputStream()
                    val fileOutput = destination?.outputStream()?.buffered()
                    try {
                        var count: Int
                        while (zip.read(buffer).also { count = it } >= 0) {
                            if (count == 0) continue
                            totalBytes += count
                            if (totalBytes > 1024L * 1024L * 1024L) throw IllegalArgumentException("Backup is larger than the 1 GB restore limit")
                            if (name == "manifest.json" && captured.size().toLong() + count > 10L * 1024L * 1024L) {
                                throw IllegalArgumentException("Backup manifest is too large")
                            }
                            if (destination == null) captured.write(buffer, 0, count)
                            else fileOutput?.write(buffer, 0, count)
                        }
                    } finally {
                        fileOutput?.close()
                    }
                    if (name == "manifest.json") manifestBytes = captured.toByteArray()
                    zip.closeEntry()
                }
            }

            val rawManifest = manifestBytes ?: throw IllegalArgumentException("Backup manifest is missing")
            val manifest = JSONObject(String(rawManifest, Charsets.UTF_8))
            if (manifest.optString("format") != "kinsake-android-backup" || manifest.optInt("version", -1) != 1) {
                throw IllegalArgumentException("This is not a supported Kinsake Android backup")
            }
            val items = manifest.optJSONArray("memories") ?: throw IllegalArgumentException("Backup memory list is missing")
            val restored = mutableListOf<Memory>()
            val mediaDirectory = File(app.filesDir, "memories/restored").apply { mkdirs() }
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val kind = item.optString("kind")
                if (kind !in setOf("photo", "milestone", "voice")) throw IllegalArgumentException("Backup contains an unsupported memory type")
                val title = item.optString("title").take(500)
                val note = item.optString("note").take(5000)
                val dateMillis = item.optLong("dateMillis", 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
                val mediaName = item.optString("mediaName").takeIf { it.isNotBlank() && it != "null" }
                var restoredPath: String? = null
                if (mediaName != null) {
                    if (!mediaName.matches(Regex("media/[A-Za-z0-9_.-]{1,80}"))) throw IllegalArgumentException("Backup has an invalid media reference")
                    val staged = File(staging, mediaName.substringAfter("media/"))
                    if (!staged.isFile) throw IllegalArgumentException("A media file referenced by the backup is missing")
                    val ext = staged.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
                    val destination = File(mediaDirectory, "restored_${UUID.randomUUID()}.${ext}")
                    staged.copyTo(destination, overwrite = false)
                    installedFiles += destination
                    restoredPath = destination.absolutePath
                }
                val childId = item.optString("childId", "default-child").takeIf { it.isNotBlank() } ?: "default-child"
                restored += Memory(kind = kind, title = title, note = note, dateMillis = dateMillis, filePath = restoredPath, childId = childId)
            }
            dao.insertAll(restored)
            (manifest.optJSONArray("profiles") ?: JSONArray().put(JSONObject().put("id", "default-child").put("name", "").put("birthDate", manifest.optJSONObject("profile")?.optString("birthDate", "") ?: "")))
        } catch (error: Throwable) {
            installedFiles.forEach { it.delete() }
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        val root = File(app.filesDir, "memories")
        if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }
}
