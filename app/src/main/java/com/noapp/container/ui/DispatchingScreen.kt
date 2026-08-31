package com.noapp.container.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val DISPATCH_DELAY_MS = 2000L

/**
 * Shown instead of an instant dispatch when useAllSlotsInDirectMode leaves no OS
 * shortcut reserved for Configure — the gear here is the only way back, tappable
 * for a couple seconds before [onDispatch] fires on its own.
 */
@Composable
fun DispatchingScreen(onOpenConfig: () -> Unit, onDispatch: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(DISPATCH_DELAY_MS)
        onDispatch()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onOpenConfig,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configure",
                    tint = LocalContentColor.current.copy(alpha = 0.4f)
                )
            }
        }
    }
}
