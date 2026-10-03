package com.verimark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verimark.app.billing.BillingRepository
import com.verimark.app.billing.EntitlementState
import com.verimark.app.billing.FreeProjectCounter
import com.verimark.app.billing.FreeProjectLimit
import com.verimark.app.data.CaseDao
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.CaseWithMarkerCount
import com.verimark.app.data.MarkerDao
import com.verimark.app.data.VeriMarkDatabase
import com.verimark.app.portable.VeriMarkImporter
import com.verimark.app.util.detectMediaType
import com.verimark.app.util.readDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VeriMarkDatabase.get(application)
    private val caseDao: CaseDao = db.caseDao()
    private val markerDao: MarkerDao = db.markerDao()
    private val billing = BillingRepository.get(application)

    /** The current Pro entitlement, driven by Google Play purchase state. */
    val entitlement: StateFlow<EntitlementState> = billing.entitlement

    /** All projects with their marker counts, newest first. */
    val projects: StateFlow<List<CaseWithMarkerCount>> = caseDao.casesWithMarkerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lifetime count of new projects created on the free tier (never decrements). */
    private val _freeProjectCreationsUsed =
        MutableStateFlow(FreeProjectCounter.used(application))
    val freeProjectCreationsUsed: StateFlow<Int> = _freeProjectCreationsUsed.asStateFlow()

    /** True when a free user has consumed all 3 lifetime project creations. */
    val isAtProjectLimit: StateFlow<Boolean> =
        combine(entitlement, freeProjectCreationsUsed) { ent, used ->
            FreeProjectLimit.isCreationBlocked(ent.isPro, used)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @Volatile
    private var creatingProject = false

    init {
        billing.start()
    }

    /** Creates a new project, or returns null when a free user has used all 3 creations. */
    suspend fun createProject(title: String, uri: Uri): Long? {
        if (creatingProject) return null
        creatingProject = true
        try {
            val isPro = entitlement.value.isPro
            if (FreeProjectLimit.isCreationBlocked(isPro, _freeProjectCreationsUsed.value)) {
                return null
            }
            val resolvedTitle = title.ifBlank { readDisplayName(getApplication(), uri) ?: "Untitled Case" }
            val id = caseDao.insert(
                CaseEntity(
                    title = resolvedTitle,
                    date = System.currentTimeMillis(),
                    videoUri = uri.toString(),
                    mediaType = detectMediaType(getApplication(), uri)
                )
            )
            // Increment only after the project was successfully created.
            if (!isPro) {
                _freeProjectCreationsUsed.value = FreeProjectCounter.increment(getApplication())
            }
            return id
        } finally {
            creatingProject = false
        }
    }

    /** Imports a portable .verimark package. Never consumes a free creation. */
    suspend fun importProject(source: Uri): Long =
        VeriMarkImporter.import(getApplication(), source, caseDao, markerDao).caseId

    /** Deletes a project; its markers are cascade-deleted via the foreign key. */
    fun deleteProject(case: CaseEntity) {
        viewModelScope.launch {
            caseDao.deleteCase(case)
        }
    }
}
