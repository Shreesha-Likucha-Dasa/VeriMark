package com.verimark.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Serializable snapshot of a single marker. */
data class MarkerBackup(
    val videoUri: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long
)

/** Serializable snapshot of a full case project. */
data class ProjectBackup(
    val caseTitle: String,
    val videoUri: String,
    val markers: List<MarkerBackup>
) {

    fun toJson(): String {
        val root = JSONObject()
        root.put("caseTitle", caseTitle)
        root.put("videoUri", videoUri)
        val array = JSONArray()
        markers.forEach { marker ->
            array.put(
                JSONObject().apply {
                    put("videoUri", marker.videoUri)
                    put("positionMs", marker.positionMs)
                    put("label", marker.label)
                    put("createdAt", marker.createdAt)
                }
            )
        }
        root.put("markers", array)
        return root.toString(2)
    }

    companion object {
        fun fromJson(json: String): ProjectBackup? = try {
            val root = JSONObject(json)
            val caseTitle = root.optString("caseTitle", "")
            val videoUri = root.optString("videoUri", "")
            val markersArray = root.optJSONArray("markers")
            val markers = mutableListOf<MarkerBackup>()
            if (markersArray != null) {
                for (i in 0 until markersArray.length()) {
                    val marker = markersArray.getJSONObject(i)
                    markers.add(
                        MarkerBackup(
                            videoUri = marker.optString("videoUri", videoUri),
                            positionMs = marker.optLong("positionMs", 0L),
                            label = marker.optString("label", ""),
                            createdAt = marker.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            ProjectBackup(caseTitle = caseTitle, videoUri = videoUri, markers = markers)
        } catch (e: Exception) {
            null
        }
    }
}
