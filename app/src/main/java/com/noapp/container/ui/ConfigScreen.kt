package com.noapp.container.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.noapp.container.icon.SlotIcon
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType

private const val MAX_FILL_SELECTION = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    mode: AppMode,
    slots: List<ShortcutSlot>,
    onEditSlot: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onModeChanged: (AppMode) -> Unit,
    onSlotsChanged: (List<ShortcutSlot>) -> Unit
) {
    var showFillDialog by remember { mutableStateOf(false) }
    val dragState = rememberSlotDragState(slots)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("No App") },
                actions = {
                    AssistChip(
                        onClick = { onModeChanged(if (mode == AppMode.LIST) AppMode.DIRECT else AppMode.LIST) },
                        label = { Text(if (mode == AppMode.LIST) "☰ List" else "▶ Direct") }
                    )
                    TextButton(onClick = { showFillDialog = true }) { Text("Fill") }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            itemsIndexed(dragState.items, key = { _, d -> d.stableKey }) { index, draggable ->
                val slot = draggable.slot
                val isDragging = dragState.draggedIndex == index
                val positionLabel = if (mode == AppMode.DIRECT && index == 0) "Main · tap icon" else "Item ${index + 1}"
                ListItem(
                    headlineContent = { Text(slot.label.ifBlank { positionLabel }) },
                    supportingContent = {
                        Text(
                            "${slot.type?.name ?: "Not configured"} · $positionLabel",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { SlotIcon(slot, size = 40.dp) },
                    trailingContent = {
                        Row {
                            Text(
                                "✕",
                                modifier = Modifier.clickable(enabled = slot.isConfigured) {
                                    onSlotsChanged(slots.toMutableList().also { it[index] = ShortcutSlot(id = index) })
                                }
                            )
                            Spacer(Modifier.width(16.dp))
                            Icon(Icons.Default.Menu, contentDescription = "Drag to reorder")
                        }
                    },
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .then(if (isDragging) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragState.dragOffsetY
                                scaleX = 1.03f
                                scaleY = 1.03f
                                shadowElevation = 12f
                            }
                        }
                        .onSizeChanged { dragState.onRowSized(it.height) }
                        .pointerInput(draggable.stableKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragState.onDragStart(index) },
                                onDragEnd = { dragState.onDragEnd(onSlotsChanged) },
                                onDragCancel = dragState::onDragCancel,
                                onDrag = { change, drag -> change.consume(); dragState.onDrag(drag.y) }
                            )
                        }
                        .clickable(enabled = dragState.draggedIndex < 0) { onEditSlot(slot.id) }
                )
                HorizontalDivider()
            }
        }
    }

    if (showFillDialog) {
        AppPickerDialog(
            multiSelect = true,
            maxSelection = MAX_FILL_SELECTION,
            onDismiss = { showFillDialog = false },
            onConfirm = { picks ->
                // Fills existing empty slots in order first, then appends any leftover picks.
                // Never overwrites an already-configured slot.
                val updated = slots.toMutableList()
                var pickIndex = 0
                for (i in updated.indices) {
                    if (pickIndex >= picks.size) break
                    if (!updated[i].isConfigured) {
                        val (pkg, appLabel) = picks[pickIndex++]
                        updated[i] = updated[i].copy(type = SlotType.APP, label = appLabel, param = pkg)
                    }
                }
                val remaining = picks.drop(pickIndex).mapIndexed { i, (pkg, appLabel) ->
                    ShortcutSlot(id = updated.size + i, type = SlotType.APP, label = appLabel, param = pkg)
                }
                onSlotsChanged(updated + remaining)
                showFillDialog = false
            }
        )
    }
}
