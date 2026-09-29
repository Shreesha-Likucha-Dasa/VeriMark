package com.verimark.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.verimark.app.ui.AppNavHost
import com.verimark.app.ui.theme.VeriMarkTheme
import com.verimark.app.util.takePersistableReadPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val _sharedVideo = MutableStateFlow<Uri?>(null)
    val sharedVideo: StateFlow<Uri?> = _sharedVideo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            VeriMarkTheme {
                AppNavHost(
                    sharedVideo = sharedVideo,
                    onSharedConsumed = { _sharedVideo.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: intent.data
        if (uri == null) return
        takePersistableReadPermission(this, uri)
        _sharedVideo.value = uri
    }
}
