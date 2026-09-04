package com.noapp.container.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.noapp.container.R

/**
 * Shared by ConfigScreen (mode picker) and SettingsScreen (floating-button toggle) so both
 * places that can turn on the peek bubble explain the "draw over other apps" permission with
 * the same copy instead of drifting apart.
 */
@Composable
fun PeekOverlayPermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_peek_overlay_dialog_title)) },
        text = { Text(stringResource(R.string.settings_peek_overlay_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.settings_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

/**
 * Shared by ConfigScreen (mode picker, on selecting Direct) and SettingsScreen ("Use all
 * shortcut slots" toggle) so both places that can turn on the always-on-in-Direct gear
 * (GearOverlayService) explain the "draw over other apps" permission with the same copy.
 */
@Composable
fun GearOverlayPermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_overlay_dialog_title)) },
        text = { Text(stringResource(R.string.settings_overlay_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.settings_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}
