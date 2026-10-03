package com.verimark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verimark.app.billing.BillingRepository
import com.verimark.app.billing.EntitlementState
import com.verimark.app.data.CaseDao
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.CaseWithMarkerCount
import com.verimark.app.data.MarkerDao
import com.verimark.app.data.VeriMarkDatabase
import com.verimark.app.portable.VeriMarkImporter
import com.verimark.app.util.detectMediaType
import com.verimark.app.util.readDisplayName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    /** True when the free project limit has been reached. */
    val isAtProjectLimit: StateFlow<Boolean> =
        combine(entitlement, projects) { ent, list ->
            ent !is EntitlementState.Pro && list.size >= BillingRepository.PROJECT_LIMIT
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        billing.start()
    }

    /** Creates a new project, or returns null when the free project limit is reached. */
    suspend fun createProject(title: String, uri: Uri): Long? {
        if (isAtLimit()) return null
        val resolvedTitle = title.ifBlank { readDisplayName(getApplication(), uri) ?: "Untitled Case" }
        return caseDao.insert(
            CaseEntity(
                title = resolvedTitle,
                date = System.currentTimeMillis(),
                videoUri = uri.toString(),
                mediaType = detectMediaType(getApplication(), uri)
            )
        )
    }

    /** Imports a portable .verimark package, or returns null when the limit is reached. */
    suspend fun importProject(source: Uri): Long? {
        if (isAtLimit()) return null
        return VeriMarkImporter.import(getApplication(), source, caseDao, markerDao).caseId
    }

    private suspend fun isAtLimit(): Boolean =
        !entitlement.value.isPro && caseDao.caseCount() >= BillingRepository.PROJECT_LIMIT

    /** Deletes a project; its markers are cascade-deleted via the foreign key. */
    fun deleteProject(case: CaseEntity) {
        viewModelScope.launch {
            caseDao.deleteCase(case)
        }
    }
}
