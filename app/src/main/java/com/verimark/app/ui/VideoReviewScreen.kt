package com.verimark.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.verimark.app.data.MediaType
import com.verimark.app.data.ProjectBackup
import com.verimark.app.pdf.generatePdfReport
import com.verimark.app.pdf.sharePdf
import com.verimark.app.util.formatMs
import com.verimark.app.util.takePersistableReadPermission

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun VideoReviewScreen(
    caseId: Long,
    onBack: () -> Unit,
    viewModel: VeriMarkViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val case by viewModel.currentCase.collectAsState()
    val videoUri by viewModel.selectedVideoUri.collectAsState()
    val markers by viewModel.markers.collectAsState()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    var showLabelDialog by remember { mutableStateOf(false) }
    var capturedPositionMs by remember { mutableLongStateOf(0L) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(caseId) {
        viewModel.loadCase(caseId)
    }

    LaunchedEffect(Unit) {
        viewModel.bindPlayer(player)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindPlayer()
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(videoUri) {
        val uri = videoUri
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            takePersistableReadPermission(context, uri)
            viewModel.setMediaForCurrentCase(uri)
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val backup = json?.let { ProjectBackup.fromJson(it) }
            if (backup != null) {
                viewModel.importBackup(backup)
                Toast.makeText(context, "Project imported", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Import failed: invalid file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportPdf = {
        val currentCase = viewModel.currentCase.value
        if (currentCase != null) {
            val file = generatePdfReport(context, currentCase, viewModel.markers.value)
            sharePdf(context, file)
        }
    }

    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportJsonPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "Project exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportProject = {
        val backup = viewModel.buildBackup()
        if (backup != null) {
            pendingExportJson = backup.toJson()
            exportJsonPicker.launch("VeriMark_Case_${caseId}.json")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(case?.title ?: "VeriMark") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { videoPicker.launch(arrayOf("video/*", "audio/*")) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Media")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export PDF Report") },
                                onClick = {
                                    menuExpanded = false
                                    exportPdf()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Project (.json)") },
                                onClick = {
                                    menuExpanded = false
                                    exportProject()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Project") },
                                onClick = {
                                    menuExpanded = false
                                    importPicker.launch(
                                        arrayOf("application/json", "application/octet-stream", "text/*")
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Markers") },
                                onClick = {
                                    menuExpanded = false
                                    showClearAllDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    capturedPositionMs = player.currentPosition
                    player.pause()
                    showLabelDialog = true
                },
                icon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                text = { Text("MARK INCIDENT") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Player occupies the top half (video surface or audio player).
            val isAudio = case?.mediaType == MediaType.AUDIO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (isAudio) MaterialTheme.colorScheme.surface else Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isAudio) {
                    AudioPlayer(player = player, title = case?.title.orEmpty())
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                controllerAutoShow = true
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                                this.player = player
                            }
                        },
                        update = { view -> view.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (videoUri == null) {
                    Text(
                        text = "Tap + to open a local video or audio file",
                        color = if (isAudio) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Marker timeline occupies the bottom half.
            if (markers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No markers yet. Play the media and tap MARK INCIDENT.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(markers, key = { it.id }) { marker ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.seekTo(marker.positionMs) }
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = formatMs(marker.positionMs),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = marker.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.deleteMarker(marker) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete marker",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Markers") },
            text = { Text("This will permanently delete all markers for this case. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllMarkers()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLabelDialog) {
        LabelDialog(
            timestampMs = capturedPositionMs,
            onDismiss = { showLabelDialog = false },
            onConfirm = { label ->
                viewModel.addMarker(label)
                showLabelDialog = false
            }
        )
    }
}

@Composable
private fun LabelDialog(
    timestampMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark Incident") },
        text = {
            Column {
                Text("Timestamp: ${formatMs(timestampMs)}")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g., Impact)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank()) onConfirm(label.trim()) else onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
