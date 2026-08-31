package com.noapp.container.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.noapp.container.model.ShortcutSlot
import kotlin.math.roundToInt

/** [stableKey] never changes mid-drag, so LazyColumn/animateItem see a move, not a delete+insert. */
data class DraggableSlot(val stableKey: Int, val slot: ShortcutSlot)

/**
 * Continuous drag-to-reorder for the fixed 5-row slot list. `id` on the underlying
 * slots is only "current position" — it's reassigned exactly once, on drop, not on
 * every intermediate swap (those are cheap list moves; committing to ConfigStore +
 * ShortcutManagerCompat on every pixel of drag would not be).
 */
class SlotDragState(initial: List<ShortcutSlot>) {
    // Monotonic, never reused — so a row removed by swipe/clear can never hand its key (and
    // with it, its composable state, e.g. mid-swipe SwipeToDismissBoxState) to the row that
    // shifts into its old position.
    private var nextKey = 0
    private fun newKey() = nextKey++

    var items by mutableStateOf(initial.map { DraggableSlot(newKey(), it) })
        private set
    var draggedIndex by mutableStateOf(-1)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    // ponytail: assumes uniform row height (all rows are one-line ListItems) — fine at 5 rows.
    private var rowHeightPx = 0

    fun onRowSized(heightPx: Int) {
        rowHeightPx = heightPx
    }

    fun onDragStart(index: Int) {
        draggedIndex = index
        dragOffsetY = 0f
    }

    fun onDrag(deltaY: Float) {
        val from = draggedIndex
        if (from < 0 || rowHeightPx == 0) return
        dragOffsetY += deltaY
        val target = (from + (dragOffsetY / rowHeightPx).roundToInt()).coerceIn(0, items.lastIndex)
        if (target != from) {
            items = items.toMutableList().apply { add(target, removeAt(from)) }
            dragOffsetY -= (target - from) * rowHeightPx
            draggedIndex = target
        }
    }

    fun onDragEnd(onCommit: (List<ShortcutSlot>) -> Unit) {
        onCommit(items.mapIndexed { i, d -> d.slot.copy(id = i) })
        draggedIndex = -1
        dragOffsetY = 0f
    }

    fun onDragCancel() {
        draggedIndex = -1
        dragOffsetY = 0f
    }

    /** Ignored mid-drag so an external recomposition (e.g. edit-screen save) can't yank the list underfoot. */
    fun resync(slots: List<ShortcutSlot>) {
        if (draggedIndex < 0) items = slots.map { DraggableSlot(newKey(), it) }
    }
}

@Composable
fun rememberSlotDragState(slots: List<ShortcutSlot>): SlotDragState {
    val state = remember { SlotDragState(slots) }
    // Keyed on a content snapshot, not the (possibly same-instance, mutated-in-place) list
    // reference itself — otherwise an in-place SnapshotStateList mutation (e.g. bulk-fill)
    // wouldn't be seen as a "changed key" and this would never resync.
    LaunchedEffect(slots.toList()) { state.resync(slots) }
    return state
}
