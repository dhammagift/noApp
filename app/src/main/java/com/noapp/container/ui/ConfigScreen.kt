package com.noapp.container.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.noapp.container.R
import com.noapp.container.icon.AndroidIcon
import com.noapp.container.icon.BoltIcon
import com.noapp.container.icon.ExtensionIcon
import com.noapp.container.icon.LinkIcon
import com.noapp.container.icon.SlotIcon
import com.noapp.container.icon.displayName
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType

private const val MAX_FILL_SELECTION = 20

private fun SlotType.icon(): ImageVector = when (this) {
    SlotType.APP -> AndroidIcon
    SlotType.URL -> LinkIcon
    SlotType.INTENT -> BoltIcon
    SlotType.CUSTOM -> ExtensionIcon
}

private fun AppMode.labelRes(): Int = when (this) {
    AppMode.LIST -> R.string.config_mode_list
    AppMode.DIRECT -> R.string.config_mode_direct
    AppMode.MIX -> R.string.config_mode_mix
}

private fun AppMode.descriptionRes(): Int = when (this) {
    AppMode.LIST -> R.string.config_mode_list_desc
    AppMode.DIRECT -> R.string.config_mode_direct_desc
    AppMode.MIX -> R.string.config_mode_mix_desc
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    mode: AppMode,
    slots: List<ShortcutSlot>,
    onEditSlot: (Int) -> Unit,
    onAddSlot: (SlotType) -> Unit,
    onOpenSettings: () -> Unit,
    onModeChanged: (AppMode) -> Unit,
    onSlotsChanged: (List<ShortcutSlot>) -> Unit
) {
    var showFillDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    val dragState = rememberSlotDragState(slots)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.common_undo)

    fun removeWithUndo(previous: List<ShortcutSlot>, updated: List<ShortcutSlot>, message: String) {
        // previous is a live SnapshotStateList reference (see slots: List<ShortcutSlot> above) —
        // snapshot it to a real immutable copy before mutating, or Undo would just reapply
        // the already-mutated list onto itself.
        val previousSnapshot = previous.toList()
        onSlotsChanged(updated)
        scope.launch {
            val result = snackbarHostState.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                onSlotsChanged(previousSnapshot)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    var modeMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(
                            onClick = { modeMenuExpanded = true },
                            label = { Text(stringResource(mode.labelRes())) }
                        )
                        DropdownMenu(
                            expanded = modeMenuExpanded,
                            onDismissRequest = { modeMenuExpanded = false }
                        ) {
                            AppMode.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = {
                                        Column(Modifier.width(260.dp)) {
                                            Text(stringResource(candidate.labelRes()), style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                stringResource(candidate.descriptionRes()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (candidate == mode) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        onModeChanged(candidate)
                                        modeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    TextButton(onClick = { showFillDialog = true }) { Text(stringResource(R.string.config_fill)) }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.config_settings_desc))
                    }
                }
            )
        },
        floatingActionButton = {
            val fabRotation by animateFloatAsState(
                if (fabExpanded) 225f else 0f,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "fabRotation"
            )
            val fabContainerColor by animateColorAsState(
                if (fabExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                label = "fabContainer"
            )
            val fabContentColor by animateColorAsState(
                if (fabExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                label = "fabContent"
            )
            Column(horizontalAlignment = Alignment.End) {
                // The whole menu grows out of the FAB's corner as one shape, instead of
                // each row popping in independently.
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn(tween(220)) +
                        expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ) +
                        scaleIn(
                            transformOrigin = TransformOrigin(1f, 1f),
                            initialScale = 0.4f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ),
                    exit = fadeOut(tween(150)) +
                        shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(200)) +
                        scaleOut(transformOrigin = TransformOrigin(1f, 1f), targetScale = 0.4f, animationSpec = tween(200))
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        // Closest-to-thumb (bottom, right above the FAB) is the most-used type first.
                        SlotType.entries.reversed().forEach { t ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 14.dp)
                            ) {
                                Text(
                                    t.displayName(),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        shadow = Shadow(MaterialTheme.colorScheme.surface, blurRadius = 8f)
                                    ),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                SmallFloatingActionButton(
                                    onClick = { fabExpanded = false; onAddSlot(t) },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(t.icon(), contentDescription = t.displayName())
                                }
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.config_add_item_desc),
                        modifier = Modifier.graphicsLayer { rotationZ = fabRotation }
                    )
                }
            }
        }
    ) { padding ->
        val notConfiguredLabel = stringResource(R.string.config_not_configured)
        val deleteDesc = stringResource(R.string.config_delete_desc)
        val reorderDesc = stringResource(R.string.config_reorder_desc)
        val mainPositionLabel = stringResource(R.string.config_position_main)
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            itemsIndexed(dragState.items, key = { _, d -> d.stableKey }) { index, draggable ->
                val slot = draggable.slot
                val isDragging = dragState.draggedIndex == index
                val positionLabel = if (mode != AppMode.LIST && index == 0) mainPositionLabel else stringResource(R.string.common_item_n, index + 1)
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            removeWithUndo(
                                previous = slots,
                                updated = slots.filterIndexed { i, _ -> i != index }.mapIndexed { i, s -> s.copy(id = i) },
                                message = context.getString(R.string.config_removed_named, slot.label.ifBlank { positionLabel })
                            )
                        }
                        true
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = deleteDesc, tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                ) {
                    ListItem(
                        headlineContent = { Text(slot.label.ifBlank { positionLabel }) },
                        supportingContent = {
                            Text(
                                "${slot.type?.displayName() ?: notConfiguredLabel} · $positionLabel",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = { SlotIcon(slot, size = 40.dp) },
                        trailingContent = {
                            Row {
                                Text(
                                    "✕",
                                    modifier = Modifier.clickable {
                                        if (slot.isConfigured) {
                                            removeWithUndo(
                                                previous = slots,
                                                updated = slots.toMutableList().also { it[index] = ShortcutSlot(id = index) },
                                                message = context.getString(R.string.config_cleared_named, slot.label.ifBlank { positionLabel })
                                            )
                                        } else {
                                            removeWithUndo(
                                                previous = slots,
                                                updated = slots.filterIndexed { i, _ -> i != index }.mapIndexed { i, s -> s.copy(id = i) },
                                                message = context.getString(R.string.config_removed_plain, positionLabel)
                                            )
                                        }
                                    }
                                )
                                Spacer(Modifier.width(16.dp))
                                Icon(Icons.Default.Menu, contentDescription = reorderDesc)
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
                }
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
