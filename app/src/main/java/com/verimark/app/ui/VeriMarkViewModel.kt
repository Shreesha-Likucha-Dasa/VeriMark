package com.verimark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.verimark.app.billing.BillingRepository
import com.verimark.app.billing.EntitlementState
import com.verimark.app.data.CaseDao
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.MarkerDao
import com.verimark.app.data.MarkerEntity
import com.verimark.app.data.MediaType
import com.verimark.app.data.ProjectBackup
import com.verimark.app.data.VeriMarkDatabase
import com.verimark.app.portable.VeriMarkImporter
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
import kotlinx.coroutines.flow.combine
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

    private val billing = BillingRepository.get(application)

    /** The current Pro entitlement, driven by Google Play purchase state. */
    val entitlement: StateFlow<EntitlementState> = billing.entitlement

    /** True when the open case has reached the free marker limit. */
    val isAtMarkerLimit: StateFlow<Boolean> =
        combine(entitlement, markers) { ent, marks ->
            ent !is EntitlementState.Pro && marks.size >= BillingRepository.MARKER_LIMIT
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        billing.start()
    }

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
            if (!entitlement.value.isPro &&
                markerDao.markerCount(case.id) >= BillingRepository.MARKER_LIMIT
            ) {
                return@launch
            }
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
    suspend fun importPackage(source: Uri): Long {
        val result = VeriMarkImporter.import(getApplication(), source, caseDao, markerDao)
        _currentCase.value = result.case
        _selectedMediaUri.value = Uri.parse(result.mediaUri)
        return result.caseId
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
