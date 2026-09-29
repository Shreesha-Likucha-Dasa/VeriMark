package com.verimark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.verimark.app.util.formatMs
import kotlinx.coroutines.delay

private const val SEEK_STEP_MS = 10_000L

/**
 * Polished audio playback UI. ExoPlayer handles decoding; this composable only
 * renders transport controls and a progress slider — the file is never fully
 * decoded into memory.
 */
@Composable
fun AudioPlayer(player: ExoPlayer, title: String) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(player) {
        while (true) {
            val pos = player.currentPosition
            val dur = player.duration
            positionMs = if (pos > 0) pos else 0L
            durationMs = if (dur > 0) dur else 0L
            isPlaying = player.isPlaying
            delay(250)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorText = error.message ?: "Playback failed"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    errorText = null
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = title.ifBlank { "Audio" },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1
        )
        Text(
            text = "AUDIO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        errorText?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Slider(
            value = positionMs.toFloat(),
            onValueChange = { player.seekTo(it.toLong()) },
            valueRange = 0f..(durationMs.takeIf { it > 0 } ?: 1L).toFloat(),
            enabled = durationMs > 0,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatMs(positionMs), style = MaterialTheme.typography.labelLarge)
            Text(
                if (durationMs > 0) formatMs(durationMs) else "--:--",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val target = (player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L)
                    player.seekTo(target)
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.Replay10,
                    contentDescription = "Back 10 seconds",
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(44.dp)
                )
            }

            IconButton(
                onClick = {
                    val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    val target = (player.currentPosition + SEEK_STEP_MS).coerceAtMost(max)
                    player.seekTo(target)
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
