package com.noapp.container.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
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
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    slots: List<ShortcutSlot>,
    onEditSlot: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onSlotsChanged: (List<ShortcutSlot>) -> Unit
) {
    var showFillDialog by remember { mutableStateOf(false) }
    val dragState = rememberSlotDragState(slots)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("No App") },
                actions = {
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
                val positionLabel = if (index == 0) "Main · tap icon" else "Shortcut $index"
                ListItem(
                    headlineContent = { Text(slot.label.ifBlank { positionLabel }) },
                    supportingContent = {
                        Text(
                            "${slot.type?.name ?: "Not configured"} · $positionLabel",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { SlotIcon(slot, size = 40.dp) },
                    trailingContent = { Icon(Icons.Default.Menu, contentDescription = "Drag to reorder") },
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
            maxSelection = slots.size,
            onDismiss = { showFillDialog = false },
            onConfirm = { picks ->
                val updated = slots.toMutableList()
                picks.forEachIndexed { i, (pkg, appLabel) ->
                    updated[i] = updated[i].copy(type = SlotType.APP, label = appLabel, param = pkg)
                }
                onSlotsChanged(updated)
                showFillDialog = false
            }
        )
    }
}
