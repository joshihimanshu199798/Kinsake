package com.kinsake.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kinsake.app.data.Memory
import com.kinsake.app.data.MemoryRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

private val Rose = Color(0xFFC77F76)
private val Ink = Color(0xFF303630)
private val Cream = Color(0xFFFAF8F4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Rose, background = Cream)) { KinsakeApp() } }
    }
}

@Composable
private fun KinsakeApp() {
    val context = LocalContext.current
    val repository = remember { MemoryRepository(context) }
    val scope = rememberCoroutineScope()
    val memories by repository.memories.collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf("Home") }
    var selected by remember { mutableStateOf<Memory?>(null) }
    var showAddMilestone by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var storageUsed by remember { mutableLongStateOf(0L) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var birthDate by remember { mutableStateOf(context.getSharedPreferences("kinsake-profile", Context.MODE_PRIVATE).getString("birthDate", "") ?: "") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            var imported = 0
            uris.forEach { uri -> runCatching { repository.importPhoto(uri); imported++ } }
            storageUsed = repository.storageUsedBytes()
            message = "Imported $imported of ${uris.size} photos"
        }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                val dir = File(context.filesDir, "memories/voice").apply { mkdirs() }
                val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
                val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare(); mediaRecorder.start()
                recorder = mediaRecorder; recordingFile = file; recording = true
                message = "Recording started"
            }.onFailure { message = "Could not start recording: ${it.localizedMessage}" }
        } else message = "Microphone permission is needed to record a voice memory"
    }

    LaunchedEffect(memories) { storageUsed = repository.storageUsedBytes() }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Home", "Photos", "Milestones", "Voice", "Profile").forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(when(item) { "Home" -> "⌂"; "Photos" -> "▧"; "Milestones" -> "✦"; "Voice" -> "♫"; else -> "◎" }) }, label = { Text(item) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("kinsake", style = MaterialTheme.typography.titleLarge, color = Rose, fontWeight = FontWeight.Bold)
            Text("Your family's little universe", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(18.dp))
            when (tab) {
                "Home" -> {
                    Text("Every little moment matters.", style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Your memories stay on this phone.", color = Color.Gray)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("Photos", memories.count { it.kind == "photo" }.toString(), Modifier.weight(1f))
                        SummaryCard("Milestones", memories.count { it.kind == "milestone" }.toString(), Modifier.weight(1f))
                        SummaryCard("Voice", memories.count { it.kind == "voice" }.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Recently added", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    MemoryGrid(memories.take(6), birthDate, onOpen = { selected = it }, onDelete = { scope.launch { repository.delete(it) } })
                    Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Add photos") }
                }
                "Photos" -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Family photos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Add") }
                    }
                    MemoryGrid(memories.filter { it.kind == "photo" }, birthDate, onOpen = { selected = it }, onDelete = { scope.launch { repository.delete(it) } })
                }
                "Milestones" -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Milestones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Button(onClick = { showAddMilestone = true }) { Text("Add") }
                    }
                    memories.filter { it.kind == "milestone" }.forEach { item ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp)) { Text(item.title, fontWeight = FontWeight.Bold); if (item.note.isNotBlank()) Text(item.note); Text(dateText(item.dateMillis) + (ageText(birthDate, item.dateMillis)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                        }
                    }
                }
                "Voice" -> {
                    Text("Voice memories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (recording) {
                            val activeRecorder = recorder
                            val file = recordingFile
                            recorder = null
                            recording = false
                            try {
                                activeRecorder?.stop()
                                activeRecorder?.release()
                                if (file == null || !file.exists() || file.length() == 0L) {
                                    file?.delete()
                                    message = "No audio was captured. Please try recording again."
                                } else {
                                    scope.launch {
                                        runCatching {
                                            repository.addVoice(file, "Voice memory · ${dateText(System.currentTimeMillis())}")
                                        }.onSuccess {
                                            message = "Voice memory saved on this phone"
                                        }.onFailure {
                                            file.delete()
                                            message = "Recording could not be saved"
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                runCatching { activeRecorder?.release() }
                                file?.delete()
                                message = "Recording was too short or could not be saved"
                            }
                        } else {
                            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) { Text(if (recording) "Stop and save" else "Start recording") }
                    memories.filter { it.kind == "voice" }.forEach { item ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title); ageText(birthDate, item.dateMillis)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Rose) } }; TextButton(onClick = {
                                item.filePath?.let { path ->
                                    runCatching {
                                        player?.release()
                                        player = MediaPlayer().apply {
                                            setDataSource(path)
                                            setOnCompletionListener { finished ->
                                                finished.release()
                                                if (player === finished) player = null
                                            }
                                            prepare()
                                            start()
                                        }
                                    }.onFailure { message = "Could not play this voice memory" }
                                }
                            }) { Text("Play") } }
                        }
                    }
                }
                else -> {
                    Text("Family profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text("Child date of birth (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { context.getSharedPreferences("kinsake-profile", Context.MODE_PRIVATE).edit().putString("birthDate", birthDate.trim()).apply(); message = "Child profile saved on this phone" }) { Text("Save child profile") }
                    Text("On-device storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val stat = StatFs(context.filesDir.absolutePath)
                    val available = stat.availableBytes
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Kinsake media stored", fontWeight = FontWeight.Bold)
                            Text(formatBytes(storageUsed), style = MaterialTheme.typography.headlineMedium, color = Rose)
                            Text("Available device storage: ${formatBytes(available)}")
                            Text("Original photos and voice files are saved in Kinsake's private app folder. Storage is limited by free space on your phone.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Privacy", fontWeight = FontWeight.Bold)
                    Text("No account, cloud upload or internet permission is used in this Android MVP. Uninstalling the app removes its private files, so export/backup support should be added before storing irreplaceable memories.", color = Color.Gray)
                }
            }
        }
    }

    if (showAddMilestone) AlertDialog(
        onDismissRequest = { showAddMilestone = false },
        title = { Text("New milestone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What happened?") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Add a note") })
            }
        },
        confirmButton = { TextButton(onClick = {
            if (title.isNotBlank()) scope.launch { repository.addMilestone(title, note); title = ""; note = ""; showAddMilestone = false }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showAddMilestone = false }) { Text("Cancel") } }
    )
    selected?.let { item ->
        AlertDialog(onDismissRequest = { selected = null }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            title = { Text(item.title) },
            text = { if (item.filePath != null) AsyncImage(model = File(item.filePath), contentDescription = item.title, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp), contentScale = ContentScale.Fit) else Text(item.note) })
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            runCatching { recorder?.release() }
        }
    }
    message?.let { msg -> LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); message = null } }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Rose); Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
    }
}

@Composable
private fun MemoryGrid(items: List<Memory>, birthDate: String, onOpen: (Memory) -> Unit, onDelete: (Memory) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("Your memories will appear here.", color = Color.Gray) }
    } else {
        LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), modifier = Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { memory ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column {
                        if (memory.kind == "photo" && memory.filePath != null) {
                            Box(Modifier.fillMaxWidth().height(130.dp).clickable { onOpen(memory) }) { AsyncImage(model = File(memory.filePath), contentDescription = memory.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit); ageText(birthDate, memory.dateMillis)?.let { Text(it, Modifier.align(Alignment.BottomStart).padding(7.dp).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Rose, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
                        } else {
                            Box(Modifier.fillMaxWidth().height(85.dp).background(Color(0xFFF2E7E3)).clickable { onOpen(memory) }, contentAlignment = Alignment.Center) { Text(if (memory.kind == "voice") "♫" else "✦", color = Rose, style = MaterialTheme.typography.headlineMedium) }
                        }
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(memory.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            TextButton(onClick = { onDelete(memory) }) { Text("×") }
                        }
                    }
                }
            }
        }
    }
}

private fun dateText(time: Long) = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

private fun ageText(birthDate: String, atMillis: Long): String? {
    val birth = runCatching { LocalDate.parse(birthDate) }.getOrNull() ?: return null
    val at = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    if (at.isBefore(birth)) return null
    val age = Period.between(birth, at)
    val parts = buildList {
        if (age.years > 0) add(age.years.toString() + if (age.years == 1) " year" else " years")
        if (age.months > 0) add(age.months.toString() + if (age.months == 1) " month" else " months")
        if (age.days > 0) add(age.days.toString() + if (age.days == 1) " day" else " days")
    }
    return parts.ifEmpty { listOf("Newborn") }.joinToString(", ")
}
