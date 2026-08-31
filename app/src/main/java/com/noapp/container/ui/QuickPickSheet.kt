package com.noapp.container.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noapp.container.icon.SlotIcon
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.shortcuts.ActionDispatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPickSheet(
    slots: List<ShortcutSlot>,
    sharedText: String?,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (sharedText != null) {
                Text(
                    "Send “$sharedText” to:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            slots.forEach { slot ->
                ListItem(
                    headlineContent = { Text(slot.label.ifBlank { "Slot ${slot.id}" }) },
                    leadingContent = { SlotIcon(slot, size = 32.dp) },
                    modifier = Modifier.clickable {
                        ActionDispatcher.execute(context, slot, sharedText)
                        (context as? Activity)?.finish()
                    }
                )
            }
            ListItem(
                headlineContent = { Text("Configure") },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onConfigure)
            )
        }
    }
}
