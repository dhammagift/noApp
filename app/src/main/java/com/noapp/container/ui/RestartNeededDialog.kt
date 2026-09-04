package com.noapp.container.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.noapp.container.R

/**
 * Shown after a mode or icon-variant change that needs a different launcher-alias component
 * enabled (see AppIconSwitcher's doc comment on why that can never happen while the app is
 * still open). Everything else about the change already took effect — this is only about the
 * home-screen icon and long-press shortcuts catching up. "Later" is a real, safe choice: the
 * next full close-and-reopen reconciles it on its own either way.
 */
@Composable
fun RestartNeededDialog(onRestart: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_dialog_title)) },
        text = { Text(stringResource(R.string.restart_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onRestart) { Text(stringResource(R.string.restart_dialog_restart)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.restart_dialog_later)) }
        }
    )
}
