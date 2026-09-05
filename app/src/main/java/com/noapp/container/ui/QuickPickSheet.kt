package com.noapp.container.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.noapp.container.R
import com.noapp.container.icon.AppIcon
import com.noapp.container.icon.SlotIcon
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.recents.RecentApp
import com.noapp.container.recents.RecentApps
import com.noapp.container.shortcuts.ActionDispatcher
import com.noapp.container.shortcuts.QuickPickPeekOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val ENTER_EXIT_ANIM_MS = 260
private const val DISMISS_DRAG_THRESHOLD_DP = 100
private const val DISMISS_FLING_VELOCITY_DP_PER_S = 1000

/**
 * A minimal bottom sheet of our own instead of Material3's ModalBottomSheet — that one hosts
 * its content in a Dialog with a scrim baked into the dialog window itself, which no
 * scrimColor value fully suppresses (it kept flashing even at Color.Transparent). This one
 * lives directly in the Activity's own content, so there is nothing to dim: an invisible
 * full-screen catcher handles tap-outside-to-dismiss, and a plain Animatable offset drives
 * the slide-up entrance, the drag-to-dismiss gesture, and the slide-down exit — all through
 * the same value, so there's no seam between "being dragged" and "animating closed".
 *
 * "Configure" sits in a small header row up top (least reachable spot) so the item list,
 * which ends at the very bottom of the sheet, keeps the most reachable position for real
 * items.
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
    showRecentApps: Boolean = false,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Falls back to this in-Activity pill (below) only when the "draw over other
    // apps" permission isn't granted — see requestDismiss() below.
    var peeked by remember { mutableStateOf(false) }
    BackHandler(enabled = peeked) { onDismiss() }

    if (peeked) {
        PeekPill(onClick = { peeked = false })
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Comfortably more than any real screen height — used as "fully off-screen below"
    // for both the entrance start point and the exit end point, so we never need this
    // composable's own measured height to animate it in or out.
    val offScreenPx = with(density) { 1200.dp.toPx() }
    val dismissThresholdPx = with(density) { DISMISS_DRAG_THRESHOLD_DP.dp.toPx() }
    val dismissVelocityPx = with(density) { DISMISS_FLING_VELOCITY_DP_PER_S.dp.toPx() }

    val offsetY = remember { Animatable(offScreenPx) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { offsetY.animateTo(0f, tween(ENTER_EXIT_ANIM_MS)) }

    fun requestDismiss() {
        if (dismissed) return
        dismissed = true
        scope.launch {
            offsetY.animateTo(offScreenPx, tween(ENTER_EXIT_ANIM_MS))
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
        }
    }
    BackHandler(onBack = ::requestDismiss)

    // Launching something from the list is also "leaving" it: the bubble comes back so the
    // list stays one tap away over whatever just opened, same as swiping the sheet away would.
    // (No exit animation — the launched app is already covering us.)
    fun leaveWithPeek() {
        if (allowPeek && Settings.canDrawOverlays(context)) {
            context.startService(Intent(context, QuickPickPeekOverlayService::class.java))
        }
        (context as? Activity)?.finish()
    }

    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    Box(Modifier.fillMaxSize()) {
        // No scrim drawn here on purpose — just an invisible full-screen tap target so
        // tapping outside the sheet still dismisses it.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = ::requestDismiss
                )
        )

        Surface(
            shape = BottomSheetDefaults.ExpandedShape,
            color = BottomSheetDefaults.ContainerColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, (offsetY.value + dragOffset).roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    },
                    onDragStopped = { velocity ->
                        val settled = dragOffset
                        dragOffset = 0f
                        offsetY.snapTo(settled)
                        if (settled > dismissThresholdPx || velocity > dismissVelocityPx) {
                            requestDismiss()
                        } else {
                            offsetY.animateTo(0f, tween(200))
                        }
                    }
                )
        ) {
            // navigationBarsPadding goes on the inner content, not the Surface itself: applied
            // to the Surface it shrinks the Surface's own bounds up from the true screen edge,
            // leaving a gap below it (through the transparent tap-outside catcher) that the
            // background behind the sheet shows through. Padding the content instead keeps the
            // Surface's background flush with the bottom of the screen while still keeping the
            // actual controls clear of the nav bar / gesture area.
            Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    BottomSheetDefaults.DragHandle()
                }
                var recentApps by remember { mutableStateOf<List<RecentApp>>(emptyList()) }
                if (showRecentApps) {
                    LaunchedEffect(Unit) {
                        recentApps = withContext(Dispatchers.IO) { RecentApps.query(context) }
                    }
                }
                // Recent apps and Configure share one compact header row instead of a row each —
                // there's no real content to spread across two, and every row here costs sheet
                // height that pushes the actual (real) items further from the thumb.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recentApps.isNotEmpty()) {
                        RecentAppsIcons(
                            apps = recentApps,
                            modifier = Modifier.weight(1f),
                            onLaunched = ::leaveWithPeek
                        )
                        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(onClick = onConfigure) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.quick_pick_configure_desc))
                    }
                }
                if (recentApps.isNotEmpty()) HorizontalDivider()
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
                                leaveWithPeek()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact, icon-only strip of recently-foregrounded apps (see recents/RecentApps.kt for why
 * this is usage history rather than a true running-tasks list) — deliberately not full
 * ListItem rows like the configured slots below, and deliberately sharing the Configure
 * header row (see its call site) rather than a row of its own, to stay minimal.
 */
@Composable
private fun RecentAppsIcons(apps: List<RecentApp>, modifier: Modifier = Modifier, onLaunched: () -> Unit) {
    val context = LocalContext.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        items(apps, key = { it.packageName }) { app ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = app.label) {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        onLaunched()
                    }
                    .padding(4.dp)
            ) {
                AppIcon(packageName = app.packageName, size = 32.dp)
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
