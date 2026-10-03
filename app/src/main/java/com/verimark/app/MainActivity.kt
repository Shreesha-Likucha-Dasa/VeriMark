package com.verimark.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.verimark.app.portable.VeriMarkPackage
import com.verimark.app.ui.AppNavHost
import com.verimark.app.ui.theme.VeriMarkTheme
import com.verimark.app.util.takePersistableReadPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val _sharedVideo = MutableStateFlow<Uri?>(null)
    val sharedVideo: StateFlow<Uri?> = _sharedVideo

    private val _sharedProject = MutableStateFlow<Uri?>(null)
    val sharedProject: StateFlow<Uri?> = _sharedProject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
        setContent {
            VeriMarkTheme {
                AppNavHost(
                    sharedVideo = sharedVideo,
                    sharedProject = sharedProject,
                    onVideoConsumed = { _sharedVideo.value = null },
                    onProjectConsumed = { _sharedProject.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = extractUri(intent) ?: return
        takePersistableReadPermission(this, uri)
        if (isVeriMarkProject(intent, uri)) {
            _sharedProject.value = uri
        } else {
            _sharedVideo.value = uri
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                ?: intent.data
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    private fun isVeriMarkProject(intent: Intent?, uri: Uri): Boolean {
        val type = intent?.type
        if (type != null && type.equals(VeriMarkPackage.MIME_TYPE, ignoreCase = true)) return true
        val name = displayNameOf(uri) ?: uri.lastPathSegment
        if (name != null && name.lowercase().endsWith(VeriMarkPackage.EXTENSION)) return true
        return uri.toString().lowercase().endsWith(VeriMarkPackage.EXTENSION)
    }

    private fun displayNameOf(uri: Uri): String? = try {
        contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }
}
