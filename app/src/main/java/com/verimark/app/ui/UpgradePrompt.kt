package com.verimark.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Shared, non-destructive upgrade prompt shown when a free limit is reached. */
@Composable
fun UpgradePromptDialog(
    title: String,
    message: String,
    onViewPro: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onViewPro) {
                Text("View Pro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}
