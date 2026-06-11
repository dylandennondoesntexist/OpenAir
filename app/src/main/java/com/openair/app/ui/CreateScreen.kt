package com.openair.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openair.app.audio.ClipRecorder
import com.openair.app.data.ClipRepository
import com.openair.app.domain.ClipUploadDraft
import com.openair.app.location.LastKnownLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private sealed interface CreatePhase {
    data object Idle : CreatePhase
    data object Recording : CreatePhase
    data class Review(val file: File, val durationSeconds: Int) : CreatePhase
    data object Uploading : CreatePhase
    data object Posted : CreatePhase
    data class Failed(val message: String) : CreatePhase
}

/**
 * Record → review → post. Recordings upload as pending_review; publishing
 * happens in the moderation step (Supabase dashboard for now).
 */
@Composable
fun CreateScreen(repository: ClipRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { ClipRecorder(context) }
    var phase by remember { mutableStateOf<CreatePhase>(CreatePhase.Idle) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var locationLabel by remember { mutableStateOf("Asbury Park") }

    fun startRecording() {
        if (recorder.start()) {
            elapsedSeconds = 0
            phase = CreatePhase.Recording
        } else {
            phase = CreatePhase.Failed("Could not start the microphone.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) startRecording()
    }

    fun requestRecord() {
        val hasMic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            startRecording()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun post(file: File, durationSeconds: Int) {
        phase = CreatePhase.Uploading
        scope.launch {
            val location = LastKnownLocation.coarse(context)
            val result = repository.uploadClip(
                ClipUploadDraft(
                    filePath = file.absolutePath,
                    title = title.trim().ifBlank { "Untitled clip" },
                    description = null,
                    locationLabel = locationLabel.trim().ifBlank { "Nearby" },
                    durationSeconds = durationSeconds,
                    latitude = location?.latitude,
                    longitude = location?.longitude
                )
            )
            phase = result.fold(
                onSuccess = {
                    title = ""
                    CreatePhase.Posted
                },
                onFailure = { CreatePhase.Failed(it.message ?: "Upload failed.") }
            )
        }
    }

    LaunchedEffect(phase) {
        while (phase == CreatePhase.Recording) {
            delay(1_000L)
            elapsedSeconds += 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("create_screen")
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val current = phase) {
            CreatePhase.Idle, CreatePhase.Recording -> {
                val recording = current == CreatePhase.Recording
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatClock(elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (recording) "Recording" else "Tap to record",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))
                Surface(
                    onClick = {
                        if (recording) {
                            val file = recorder.stop()
                            phase = if (file != null && elapsedSeconds >= 1) {
                                CreatePhase.Review(file, elapsedSeconds)
                            } else {
                                CreatePhase.Idle
                            }
                        } else {
                            requestRecord()
                        }
                    },
                    shape = CircleShape,
                    color = if (recording) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (recording) "Stop recording" else "Start recording",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {}) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import an audio file")
                }
                Text(
                    text = "Clips are reviewed before they go live near you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            is CreatePhase.Review -> {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Ready to post",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatClock(current.durationSeconds)} recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("What is this about?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    label = { Text("Place") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    OutlinedButton(onClick = {
                        current.file.delete()
                        phase = CreatePhase.Idle
                    }) {
                        Text("Discard")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { post(current.file, current.durationSeconds) }) {
                        Text("Post")
                    }
                }
            }

            CreatePhase.Uploading -> {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Posting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
            }

            CreatePhase.Posted -> {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Posted!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your clip goes live near you after a quick review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { phase = CreatePhase.Idle }) {
                    Text("Record another")
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            is CreatePhase.Failed -> {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { phase = CreatePhase.Idle }) {
                    Text("Try again")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
