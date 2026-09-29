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
import com.verimark.app.data.MarkerBackup
import com.verimark.app.data.MarkerDao
import com.verimark.app.data.MarkerEntity
import com.verimark.app.data.MediaType
import com.verimark.app.data.ProjectBackup
import com.verimark.app.data.VeriMarkDatabase
import com.verimark.app.util.detectMediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
class VeriMarkViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VeriMarkDatabase.get(application)
    private val caseDao: CaseDao = db.caseDao()
    private val markerDao: MarkerDao = db.markerDao()

    /** Bound ExoPlayer instance, used to read playback position and seek. */
    var player: ExoPlayer? = null
        private set

    private val _currentCase = MutableStateFlow<CaseEntity?>(null)
    val currentCase: StateFlow<CaseEntity?> = _currentCase.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

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
                _selectedVideoUri.value = Uri.parse(video)
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
        _selectedVideoUri.value = uri
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
        val uri = _selectedVideoUri.value ?: return
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

    /** Builds a serializable snapshot of the current case and markers. */
    fun buildBackup(): ProjectBackup? {
        val case = _currentCase.value ?: return null
        val uri = _selectedVideoUri.value ?: return null
        return ProjectBackup(
            caseTitle = case.title,
            videoUri = uri.toString(),
            mediaType = case.mediaType.name,
            markers = markers.value.map { marker ->
                MarkerBackup(
                    videoUri = marker.videoUri,
                    positionMs = marker.positionMs,
                    label = marker.label,
                    createdAt = marker.createdAt
                )
            }
        )
    }

    /** Restores a project from a backup, creating a new case with its markers. */
    fun importBackup(backup: ProjectBackup) {
        viewModelScope.launch {
            val title = backup.caseTitle.ifBlank { "Imported Case" }
            val now = System.currentTimeMillis()
            val caseId = caseDao.insert(
                CaseEntity(
                    title = title,
                    date = now,
                    videoUri = backup.videoUri,
                    mediaType = MediaType.fromStorage(backup.mediaType)
                )
            )
            if (backup.markers.isNotEmpty()) {
                markerDao.insertAll(
                    backup.markers.map { m ->
                        MarkerEntity(
                            caseId = caseId,
                            videoUri = m.videoUri.ifBlank { backup.videoUri },
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
                videoUri = backup.videoUri,
                mediaType = MediaType.fromStorage(backup.mediaType)
            )
            if (backup.videoUri.isNotBlank()) {
                _selectedVideoUri.value = Uri.parse(backup.videoUri)
            }
        }
    }

}
