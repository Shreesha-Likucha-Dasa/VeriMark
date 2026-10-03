package com.verimark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VeriMarkDatabase.get(application)
    private val caseDao: CaseDao = db.caseDao()
    private val markerDao: MarkerDao = db.markerDao()

    /** All projects with their marker counts, newest first. */
    val projects: StateFlow<List<CaseWithMarkerCount>> = caseDao.casesWithMarkerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a new project and returns its case id. */
    suspend fun createProject(title: String, uri: Uri): Long {
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

    /** Imports a portable .verimark package, returning the new case id. */
    suspend fun importProject(source: Uri): Long =
        VeriMarkImporter.import(getApplication(), source, caseDao, markerDao).caseId

    /** Deletes a project; its markers are cascade-deleted via the foreign key. */
    fun deleteProject(case: CaseEntity) {
        viewModelScope.launch {
            caseDao.deleteCase(case)
        }
    }
}
