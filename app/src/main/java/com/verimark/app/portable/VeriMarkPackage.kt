package com.verimark.app.portable

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, self-contained VeriMark project package (.verimark).
 *
 * A package is a ZIP containing:
 *   - project.json (versioned metadata + markers)
 *   - media/<recording file>
 *
 * Media bytes are always streamed, never fully loaded into memory.
 */
object VeriMarkPackage {

    const val FORMAT_VERSION = 1
    const val APP_NAME = "VeriMark"
    const val EXTENSION = ".verimark"
    const val PROJECT_JSON = "project.json"
    const val MEDIA_DIR = "media"

    private const val BUFFER_SIZE = 8192

    data class PortableMarker(
        val timestampMs: Long,
        val label: String,
        val createdAt: Long
    )

    data class PortableProject(
        val title: String,
        val mediaType: String,
        val mediaFileName: String,
        val markers: List<PortableMarker>
    )

    /** Raw (unparsed) package contents: project.json text + extracted media file. */
    data class RawPackage(
        val projectJson: String,
        val mediaFile: File
    )

    class PackageException(message: String) : Exception(message)

    fun projectToJson(project: PortableProject): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("app", APP_NAME)
        root.put(
            "project",
            JSONObject().apply {
                put("title", project.title)
                put("mediaType", project.mediaType)
                put("mediaFile", project.mediaFileName)
            }
        )
        val markersArray = JSONArray()
        project.markers.forEach { marker ->
            markersArray.put(
                JSONObject().apply {
                    put("timestampMs", marker.timestampMs)
                    put("label", marker.label)
                    put("createdAt", marker.createdAt)
                }
            )
        }
        root.put("markers", markersArray)
        return root.toString(2)
    }

    fun projectFromJson(json: String): PortableProject {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw PackageException("The package contains invalid project data.")
        }
        val version = root.optInt("formatVersion", -1)
        if (version != FORMAT_VERSION) {
            throw PackageException("This package uses an unsupported format version.")
        }
        val project = root.optJSONObject("project")
            ?: throw PackageException("The package is missing project information.")
        val title = project.optString("title", "").ifBlank { "Imported Project" }
        val mediaType = project.optString("mediaType", "VIDEO")
        val mediaFileName = project.optString("mediaFile", "")
        if (mediaFileName.isBlank()) {
            throw PackageException("The package is missing its recording reference.")
        }
        val markers = mutableListOf<PortableMarker>()
        root.optJSONArray("markers")?.let { array ->
            for (i in 0 until array.length()) {
                val marker = array.getJSONObject(i)
                markers.add(
                    PortableMarker(
                        timestampMs = marker.optLong("timestampMs", 0L),
                        label = marker.optString("label", ""),
                        createdAt = marker.optLong("createdAt", 0L)
                    )
                )
            }
        }
        return PortableProject(title, mediaType, mediaFileName, markers)
    }

    /** Rejects absolute paths, parent traversal, and empty segments. */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith('/')) return false
        if (name.contains('\\')) return false
        if (name.contains(':')) return false
        return name.split('/').none { it == ".." || it.isEmpty() }
    }

    /** Writes a .verimark package, streaming [mediaIn] into media/[mediaFileName]. */
    fun write(out: OutputStream, projectJson: String, mediaFileName: String, mediaIn: InputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(PROJECT_JSON))
            zip.write(projectJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(mediaFileName))
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = mediaIn.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
            }
            zip.closeEntry()
        }
    }

    /** Reads and validates a .verimark package, extracting media into [mediaDir]. */
    fun readRaw(input: InputStream, mediaDir: File): RawPackage {
        var projectJson: String? = null
        var mediaFile: File? = null
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!isSafeEntryName(name)) {
                        throw PackageException("The package contains an unsafe file path.")
                    }
                    when {
                        name == PROJECT_JSON -> {
                            projectJson = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        name.startsWith("$MEDIA_DIR/") -> {
                            val target = File(mediaDir, name)
                            if (!target.canonicalPath.startsWith(mediaDir.canonicalPath + File.separator)) {
                                throw PackageException("The package contains an unsafe file path.")
                            }
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                }
                            }
                            mediaFile = target
                        }
                        // Ignore any other entries.
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: PackageException) {
            throw e
        } catch (e: IOException) {
            throw PackageException("This is not a valid VeriMark package.")
        }

        val json = projectJson ?: throw PackageException("The package is missing project data.")
        val file = mediaFile ?: throw PackageException("The package does not contain the recording file.")
        return RawPackage(json, file)
    }

    /** Sanitizes a project title into a safe file name (without extension). */
    fun safeProjectFileName(title: String): String =
        title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "VeriMark_Project" }
}
