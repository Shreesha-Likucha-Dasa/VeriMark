package com.verimark.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.verimark.app.data.MediaType

/** Reads the display name of a content URI, if available. */
fun readDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

/** Requests a persistable URI permission so access survives app restarts. */
fun takePersistableReadPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Persistable permission unavailable; URI still usable this session.
        }
    }
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "m4a", "aac", "ogg", "opus", "flac",
    "amr", "mid", "midi", "xmf", "mxmf", "rtttl", "rtx", "ota", "imy"
)

/**
 * Determines whether a URI points at audio or video, preferring the system MIME
 * type and falling back to the file extension only when the MIME type is unknown.
 */
fun detectMediaType(context: Context, uri: Uri): MediaType {
    val mime = context.contentResolver.getType(uri).orEmpty()
    if (mime.startsWith("audio/")) return MediaType.AUDIO
    if (mime.startsWith("video/")) return MediaType.VIDEO
    val name = readDisplayName(context, uri).orEmpty()
    val extension = name.substringAfterLast('.', "").lowercase()
    return if (extension in AUDIO_EXTENSIONS) MediaType.AUDIO else MediaType.VIDEO
}

/** Returns true if [uriString] is blank or can currently be opened for reading. */
fun isUriAccessible(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return true
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.close()
        true
    }.getOrDefault(false)
}
