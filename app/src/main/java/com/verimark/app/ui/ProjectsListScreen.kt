package com.verimark.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.verimark.app.data.CaseWithMarkerCount
import com.verimark.app.data.MediaType
import com.verimark.app.portable.VeriMarkPackage
import com.verimark.app.util.formatDate
import com.verimark.app.util.takePersistableReadPermission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreen(
    onProjectClick: (Long) -> Unit,
    onOpenPro: () -> Unit,
    viewModel: ProjectsListViewModel = viewModel()
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val isAtProjectLimit by viewModel.isAtProjectLimit.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddSheet by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var pickedMedia by remember { mutableStateOf<Uri?>(null) }
    var deleteTarget by remember { mutableStateOf<CaseWithMarkerCount?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showImportError by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            takePersistableReadPermission(context, uri)
            pickedMedia = uri
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                val result = runCatching { viewModel.importProject(uri) }
                isImporting = false
                result.onSuccess { id -> onProjectClick(id) }.onFailure { e ->
                    importError = (e as? VeriMarkPackage.PackageException)?.message
                        ?: "The file is corrupted, unsupported, or not a valid VeriMark project."
                    showImportError = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VeriMark Projects") },
                actions = {
                    IconButton(onClick = onOpenPro) {
                        Icon(Icons.Filled.Star, contentDescription = "Upgrade to Pro")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Start your first review",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open a video or audio recording and mark the moments that matter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showAddSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.case.id }) { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProjectClick(item.case.id) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (item.case.mediaType == MediaType.AUDIO) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    if (item.case.mediaType == MediaType.AUDIO) {
                                        Icons.Filled.AudioFile
                                    } else {
                                        Icons.Filled.Movie
                                    },
                                    contentDescription = if (item.case.mediaType == MediaType.AUDIO) "Audio" else "Video",
                                    tint = if (item.case.mediaType == MediaType.AUDIO) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.case.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatDate(item.case.date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${item.markerCount} marker(s)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            IconButton(onClick = { deleteTarget = item }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete project")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = addSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Add to VeriMark",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                AddOptionRow(
                    icon = {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    iconColor = MaterialTheme.colorScheme.primaryContainer,
                    title = "Add Recording",
                    subtitle = "Choose a video or audio recording to review.",
                    onClick = {
                        showAddSheet = false
                        if (isAtProjectLimit) showUpgradeDialog = true else showNewDialog = true
                    }
                )
                AddOptionRow(
                    icon = {
                        Icon(
                            Icons.Filled.FileOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    iconColor = MaterialTheme.colorScheme.secondaryContainer,
                    title = "Import Project",
                    subtitle = "Open a previously exported or shared VeriMark project.",
                    onClick = {
                        showAddSheet = false
                        importPicker.launch(
                            arrayOf(
                                VeriMarkPackage.MIME_TYPE,
                                "application/zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                )
            }
        }
    }

    if (isImporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Importing project") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Preparing your project…")
                }
            },
            confirmButton = { }
        )
    }

    if (showImportError) {
        AlertDialog(
            onDismissRequest = { showImportError = false },
            title = { Text("Couldn't import this project") },
            text = { Text(importError ?: "This file isn't a valid VeriMark project.") },
            confirmButton = {
                TextButton(onClick = { showImportError = false }) { Text("OK") }
            }
        )
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewDialog = false
                pickedMedia = null
            },
            title = { Text("New Project") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Project title") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { mediaPicker.launch(arrayOf("video/*", "audio/*")) }) {
                        Text(
                            if (pickedMedia == null) "Choose Media"
                            else "Media selected — tap to change"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pickedMedia != null,
                    onClick = {
                        val uri = pickedMedia ?: return@TextButton
                        showNewDialog = false
                        scope.launch {
                            val id = viewModel.createProject(newTitle, uri)
                            newTitle = ""
                            pickedMedia = null
                            if (id != null) onProjectClick(id) else showUpgradeDialog = true
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewDialog = false
                        pickedMedia = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUpgradeDialog) {
        UpgradePromptDialog(
            title = "You've used your 3 free projects",
            message = "You've reached the free project creation limit. Upgrade to VeriMark Pro for unlimited projects, unlimited markers and watermark-free PDF reports.",
            onViewPro = {
                showUpgradeDialog = false
                onOpenPro()
            },
            onDismiss = { showUpgradeDialog = false }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Project") },
            text = { Text("Delete \"${target.case.title}\" and all its markers? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(target.case)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddOptionRow(
    icon: @Composable () -> Unit,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = iconColor, shape = RoundedCornerShape(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp)
            ) {
                icon()
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
