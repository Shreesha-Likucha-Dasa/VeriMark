package com.verimark.app.portable

import android.content.Context
import android.net.Uri
import com.verimark.app.data.CaseDao
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.MarkerDao
import com.verimark.app.data.MarkerEntity
import com.verimark.app.data.MediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared, canonical .verimark import: validates the package, extracts its media
 * into app-controlled storage, and persists the project and markers.
 */
object VeriMarkImporter {

    data class Result(
        val caseId: Long,
        val case: CaseEntity,
        val mediaUri: String
    )

    suspend fun import(
        context: Context,
        source: Uri,
        caseDao: CaseDao,
        markerDao: MarkerDao
    ): Result = withContext(Dispatchers.IO) {
        val importDir = File(context.filesDir, "verimark_import_${System.currentTimeMillis()}")
        val raw: VeriMarkPackage.RawPackage = try {
            context.contentResolver.openInputStream(source)?.use { input ->
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
        val mediaType = MediaType.fromStorage(project.mediaType)
        val caseId = caseDao.insert(
            CaseEntity(title = title, date = now, videoUri = mediaUri, mediaType = mediaType)
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
        Result(
            caseId = caseId,
            case = CaseEntity(id = caseId, title = title, date = now, videoUri = mediaUri, mediaType = mediaType),
            mediaUri = mediaUri
        )
    }
}
