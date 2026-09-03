package com.noapp.container.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noapp.container.R
import com.noapp.container.icon.SlotIcon
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.shortcuts.ActionDispatcher
import com.noapp.container.shortcuts.QuickPickPeekOverlayService

/**
 * Anchored to the bottom of the screen by ModalBottomSheet itself — easy thumb
 * reach, no extra positioning work needed. "Configure" sits in a small header
 * row up top (least reachable spot) so the item list, which ends at the very
 * bottom of the sheet, keeps the most reachable position for real items.
 *
 * [allowPeek] (LIST and MIX, not the share sheet): swiping the sheet away
 * collapses it into a small draggable button instead of finishing the host
 * Activity — tapping that button re-shows the list (with no side effect, unlike
 * a plain re-tap in MIX mode, which would also re-dispatch slot 0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPickSheet(
    slots: List<ShortcutSlot>,
    sharedText: String?,
    allowPeek: Boolean = false,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Falls back to this in-Activity pill (below) only when the "draw over other
    // apps" permission isn't granted — see the onDismissRequest branch below.
    var peeked by remember { mutableStateOf(false) }
    BackHandler(enabled = peeked) { onDismiss() }

    if (peeked) {
        PeekPill(onClick = { peeked = false })
        return
    }

    // skipPartiallyExpanded: on a short display (e.g. a folded cover screen) the default
    // "partially expanded" peek state can leave only 1-2 rows visible with no obvious hint
    // to drag further — always render fully expanded instead. MIX's own peek affordance
    // (above) is a separate, deliberately compact state, not this one.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    ModalBottomSheet(
        onDismissRequest = {
            when {
                !allowPeek -> onDismiss()
                // Preferred path: a real system overlay that keeps showing over
                // whatever the user switches to, not just this Activity's own window.
                Settings.canDrawOverlays(context) -> {
                    context.startService(Intent(context, QuickPickPeekOverlayService::class.java))
                    onDismiss()
                }
                // No overlay permission: the in-Activity pill is at least usable
                // while the user stays on top of whatever slot 0 launched.
                else -> peeked = true
            }
        },
        sheetState = sheetState
    ) {
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

/**
 * The in-Activity fallback for [allowPeek] when the overlay permission isn't
 * granted: a small round button instead of a full-width bar, so it doesn't
 * cover whatever's underneath it. Positioned bottom-end, same thumb-reach
 * corner the sheet's own controls favor. Unlike QuickPickPeekOverlayService's
 * real system overlay, this only lives as long as the Activity itself does.
 */
@Composable
private fun PeekPill(onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClickLabel = stringResource(R.string.quick_pick_reopen_desc), onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.quick_pick_reopen_desc),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
