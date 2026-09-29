package com.verimark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.verimark.app.data.CaseDao
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.MarkerDao
import com.verimark.app.data.MarkerEntity
import com.verimark.app.data.MediaType
import com.verimark.app.data.ProjectBackup
import com.verimark.app.data.VeriMarkDatabase
import com.verimark.app.portable.VeriMarkPackage
import com.verimark.app.util.detectMediaType
import com.verimark.app.util.readDisplayName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class VeriMarkViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VeriMarkDatabase.get(application)
    private val caseDao: CaseDao = db.caseDao()
    private val markerDao: MarkerDao = db.markerDao()

    /** Bound ExoPlayer instance, used to read playback position and seek. */
    var player: ExoPlayer? = null
        private set

    private val _currentCase = MutableStateFlow<CaseEntity?>(null)
    val currentCase: StateFlow<CaseEntity?> = _currentCase.asStateFlow()

    private val _selectedMediaUri = MutableStateFlow<Uri?>(null)
    val selectedMediaUri: StateFlow<Uri?> = _selectedMediaUri.asStateFlow()

    val markers: StateFlow<List<MarkerEntity>> = _currentCase
        .flatMapLatest { case ->
            if (case == null) flowOf(emptyList()) else markerDao.markersForCase(case.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Loads the case with [caseId], restoring its video URI and marker list. */
    fun loadCase(caseId: Long) {
        viewModelScope.launch {
            val case = caseDao.getCase(caseId) ?: return@launch
            _currentCase.value = case
            val video = case.videoUri.ifBlank { markerDao.firstMarkerVideoUri(caseId).orEmpty() }
            if (video.isNotBlank()) {
                _selectedMediaUri.value = Uri.parse(video)
            }
        }
    }

    fun bindPlayer(player: ExoPlayer) {
        this.player = player
        player.setSeekParameters(SeekParameters.EXACT)
    }

    fun unbindPlayer() {
        player = null
    }

    /** Replaces the media (video or audio) for the currently open case. */
    fun setMediaForCurrentCase(uri: Uri) {
        _selectedMediaUri.value = uri
        val case = _currentCase.value ?: return
        val mediaType = detectMediaType(getApplication(), uri)
        viewModelScope.launch {
            caseDao.updateMedia(case.id, uri.toString(), mediaType)
            _currentCase.value = case.copy(videoUri = uri.toString(), mediaType = mediaType)
        }
    }

    /** Inserts a marker at the current playback position. */
    fun addMarker(label: String) {
        val case = _currentCase.value ?: return
        val uri = _selectedMediaUri.value ?: return
        val positionMs = player?.currentPosition ?: 0L
        viewModelScope.launch {
            markerDao.insert(
                MarkerEntity(
                    caseId = case.id,
                    videoUri = uri.toString(),
                    positionMs = positionMs,
                    label = label,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun deleteMarker(marker: MarkerEntity) {
        viewModelScope.launch {
            markerDao.deleteMarker(marker)
        }
    }

    fun clearAllMarkers() {
        val case = _currentCase.value ?: return
        viewModelScope.launch {
            markerDao.clearAllMarkers(case.id)
        }
    }

    /** Replaces the media for the current case, removing all existing markers. */
    fun replaceMedia(uri: Uri) {
        _selectedMediaUri.value = uri
        val case = _currentCase.value ?: return
        val mediaType = detectMediaType(getApplication(), uri)
        viewModelScope.launch {
            markerDao.clearAllMarkers(case.id)
            caseDao.updateMedia(case.id, uri.toString(), mediaType)
            _currentCase.value = case.copy(videoUri = uri.toString(), mediaType = mediaType)
        }
    }

    /** Imports a legacy metadata-only JSON backup. [resolvedMediaUri] may be blank if unavailable. */
    fun importLegacyBackup(backup: ProjectBackup, resolvedMediaUri: String?) {
        viewModelScope.launch {
            val title = backup.caseTitle.ifBlank { "Imported Project" }
            val now = System.currentTimeMillis()
            val uri = resolvedMediaUri.orEmpty()
            val caseId = caseDao.insert(
                CaseEntity(
                    title = title,
                    date = now,
                    videoUri = uri,
                    mediaType = MediaType.fromStorage(backup.mediaType)
                )
            )
            if (backup.markers.isNotEmpty()) {
                markerDao.insertAll(
                    backup.markers.map { m ->
                        MarkerEntity(
                            caseId = caseId,
                            videoUri = m.videoUri.ifBlank { uri },
                            positionMs = m.positionMs,
                            label = m.label,
                            createdAt = m.createdAt
                        )
                    }
                )
            }
            _currentCase.value = CaseEntity(
                id = caseId,
                title = title,
                date = now,
                videoUri = uri,
                mediaType = MediaType.fromStorage(backup.mediaType)
            )
            if (uri.isNotBlank()) {
                _selectedMediaUri.value = Uri.parse(uri)
            }
        }
    }

    /** Imports a portable .verimark package, returning the new case id. */
    suspend fun importPackage(source: Uri): Long = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val importDir = File(app.filesDir, "verimark_import_${System.currentTimeMillis()}")
        val raw: VeriMarkPackage.RawPackage = try {
            app.contentResolver.openInputStream(source)?.use { input ->
                VeriMarkPackage.readRaw(input, importDir)
            } ?: throw VeriMarkPackage.PackageException("Cannot read the selected file.")
        } catch (e: Exception) {
            importDir.deleteRecursively()
            if (e is VeriMarkPackage.PackageException) throw e
            throw VeriMarkPackage.PackageException("This is not a valid VeriMark package.")
        }
        val project = VeriMarkPackage.projectFromJson(raw.projectJson)
        val mediaUri = Uri.fromFile(raw.mediaFile).toString()
        val title = project.title.ifBlank { "Imported Project" }
        val now = System.currentTimeMillis()
        val caseId = caseDao.insert(
            CaseEntity(
                title = title,
                date = now,
                videoUri = mediaUri,
                mediaType = MediaType.fromStorage(project.mediaType)
            )
        )
        if (project.markers.isNotEmpty()) {
            markerDao.insertAll(
                project.markers.map { m ->
                    MarkerEntity(
                        caseId = caseId,
                        videoUri = mediaUri,
                        positionMs = m.timestampMs,
                        label = m.label,
                        createdAt = if (m.createdAt > 0) m.createdAt else now
                    )
                }
            )
        }
        _currentCase.value = CaseEntity(
            id = caseId,
            title = title,
            date = now,
            videoUri = mediaUri,
            mediaType = MediaType.fromStorage(project.mediaType)
        )
        _selectedMediaUri.value = Uri.parse(mediaUri)
        caseId
    }

    /** Exports the current project as a .verimark package to [destination]. */
    suspend fun exportPackage(destination: Uri): Boolean = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val case = _currentCase.value ?: return@withContext false
        val mediaUri = _selectedMediaUri.value ?: return@withContext false
        val mediaName = readDisplayName(app, mediaUri) ?: "recording"
        val mediaFileName = "${VeriMarkPackage.MEDIA_DIR}/$mediaName"
        val project = VeriMarkPackage.PortableProject(
            title = case.title,
            mediaType = case.mediaType.name,
            mediaFileName = mediaFileName,
            markers = markers.value.map { m ->
                VeriMarkPackage.PortableMarker(m.positionMs, m.label, m.createdAt)
            }
        )
        val json = VeriMarkPackage.projectToJson(project)
        val tempFile = File.createTempFile("verimark_export_", VeriMarkPackage.EXTENSION, app.cacheDir)
        try {
            tempFile.outputStream().use { out ->
                app.contentResolver.openInputStream(mediaUri)?.use { mediaIn ->
                    VeriMarkPackage.write(out, json, mediaFileName, mediaIn)
                } ?: throw VeriMarkPackage.PackageException("Cannot read the current recording.")
            }
            app.contentResolver.openOutputStream(destination)?.use { dest ->
                tempFile.inputStream().use { src -> src.copyTo(dest) }
            } ?: throw VeriMarkPackage.PackageException("Cannot write the package.")
            true
        } finally {
            tempFile.delete()
        }
    }

    /** Builds a .verimark package in the cache directory for sharing. */
    suspend fun buildPackageFileForSharing(): File? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val case = _currentCase.value ?: return@withContext null
        val mediaUri = _selectedMediaUri.value ?: return@withContext null
        val mediaName = readDisplayName(app, mediaUri) ?: "recording"
        val mediaFileName = "${VeriMarkPackage.MEDIA_DIR}/$mediaName"
        val project = VeriMarkPackage.PortableProject(
            title = case.title,
            mediaType = case.mediaType.name,
            mediaFileName = mediaFileName,
            markers = markers.value.map { m ->
                VeriMarkPackage.PortableMarker(m.positionMs, m.label, m.createdAt)
            }
        )
        val json = VeriMarkPackage.projectToJson(project)
        val file = File(app.cacheDir, VeriMarkPackage.safeProjectFileName(case.title) + VeriMarkPackage.EXTENSION)
        try {
            file.outputStream().use { out ->
                app.contentResolver.openInputStream(mediaUri)?.use { mediaIn ->
                    VeriMarkPackage.write(out, json, mediaFileName, mediaIn)
                } ?: throw VeriMarkPackage.PackageException("Cannot read the current recording.")
            }
            file
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

}
