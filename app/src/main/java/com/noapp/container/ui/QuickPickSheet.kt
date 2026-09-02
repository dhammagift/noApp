package com.noapp.container.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noapp.container.R
import com.noapp.container.icon.SlotIcon
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.shortcuts.ActionDispatcher

/**
 * Anchored to the bottom of the screen by ModalBottomSheet itself — easy thumb
 * reach, no extra positioning work needed. "Configure" sits in a small header
 * row up top (least reachable spot) so the item list, which ends at the very
 * bottom of the sheet, keeps the most reachable position for real items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPickSheet(
    slots: List<ShortcutSlot>,
    sharedText: String?,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // skipPartiallyExpanded: on a short display (e.g. a folded cover screen) the default
    // "partially expanded" peek state can leave only 1-2 rows visible with no obvious hint
    // to drag further — always render fully expanded instead.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onConfigure) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.quick_pick_configure_desc))
                }
            }
            if (sharedText != null) {
                Text(
                    stringResource(R.string.quick_pick_send_to, sharedText),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            LazyColumn(Modifier.heightIn(max = maxListHeight)) {
                items(slots, key = { it.id }) { slot ->
                    ListItem(
                        headlineContent = { Text(slot.label.ifBlank { stringResource(R.string.common_item_n, slot.id + 1) }) },
                        leadingContent = { SlotIcon(slot, size = 32.dp) },
                        modifier = Modifier.clickable {
                            ActionDispatcher.execute(context, slot, sharedText)
                            (context as? Activity)?.finish()
                        }
                    )
                }
            }
        }
    }
}
