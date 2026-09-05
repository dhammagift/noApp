package com.noapp.container.shortcuts

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.noapp.container.QuickPickActivity
import com.noapp.container.R
import com.noapp.container.data.ConfigStore
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val PREFS_NAME = "no_app_prefs"
private const val KEY_PEEK_X = "peek_bubble_x"
private const val KEY_PEEK_Y = "peek_bubble_y"
// 0 = free-floating at KEY_PEEK_X; -1 / 1 = tucked into the left / right screen edge (X is
// then derived from the edge, so it survives a screen-size change).
private const val KEY_PEEK_DOCK = "peek_bubble_dock"
private const val BUBBLE_DP = 48
private const val MARGIN_DP = 20
private const val TAP_SLOP_DP = 8
private const val TRASH_SIZE_DP = 64
private const val TRASH_BOTTOM_MARGIN_DP = 32
private const val TRASH_ACTIVATE_RADIUS_DP = 56
// Much higher than OverScroller's own default (0.015f) — releasing the bubble should read as
// a soft, short settle, not an actual throw; combined with capping the velocity fed into it
// (see MAX_FLING_VELOCITY_DP_PER_S), even a hard flick only ever travels a small distance.
private const val FLING_FRICTION = 0.09f
private const val MAX_FLING_VELOCITY_DP_PER_S = 1500f
// Edge docking: coming to rest this close to a side edge tucks the bubble into it, leaving a
// sliver (AppConfig.peekBubbleDockPeek of its width) on screen as the handle.
private const val DOCK_ZONE_DP = 28
private const val DOCK_SCALE = 0.9f
// Docked opacity relative to the configured button opacity — out of the way means fainter too.
private const val DOCK_ALPHA_FACTOR = 0.6f
private const val DOCK_ANIM_MS = 300L
private const val UNDOCK_ANIM_MS = 160L

/**
 * MIX/LIST mode's collapsed-list affordance: a small draggable button drawn as a
 * real system overlay (TYPE_APPLICATION_OVERLAY), not Compose content inside our
 * own Activity window — so it keeps showing over whatever app slot 0 launched (or
 * any other app the user switches to), instead of disappearing the moment
 * QuickPickActivity itself isn't the foreground window. Never auto-dismisses
 * (unlike GearOverlayService): it sits wherever it's dropped until tapped (reopens
 * the list) or dragged onto the red drop target (removes it — for good, or just
 * until the next launch, per AppConfig.peekBubbleReturns).
 *
 * Only started when Settings.canDrawOverlays() is already true — see the call site
 * in QuickPickSheet.kt, which falls back to an in-Activity peek otherwise.
 *
 * Dragging carries real momentum on release (OverScroller, the same friction-based
 * fling used for scrolling) instead of snapping to a screen edge — the user should be
 * free to leave it wherever they actually let go, just decelerating naturally rather
 * than stopping dead. The one exception is the side edges: coming to rest against
 * (or being thrown at) one tucks the bubble into it — most of it slides off screen,
 * it fades and shrinks a little, and a thin sliver stays as the handle. It then takes
 * no room and still opens the list on a tap; dragging it pulls it back out. Docked
 * state is remembered, so the handle is where it was left next time too.
 */
class QuickPickPeekOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var trashView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** See the comment at its call site in [onStartCommand] for why this exists. */
    private fun activeDisplayContext(): Context {
        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager ?: return this
        val active = runCatching { displayManager.displays.firstOrNull { it.state == Display.STATE_ON } }
            .getOrNull() ?: return this
        return runCatching { createDisplayContext(active) }.getOrDefault(this)
    }

    private fun roundIconView(context: Context, sizePx: Int, iconRes: Int, bgColor: Int): ImageView =
        ImageView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            val icon = ContextCompat.getDrawable(context, iconRes)
            val iconPad = (sizePx * 0.24f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setImageBitmap(icon?.toBitmap(sizePx - iconPad * 2, sizePx - iconPad * 2))
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Already showing: leave it exactly where the user dragged it rather than
        // resetting position or stacking a second bubble.
        if (bubbleView != null) return START_NOT_STICKY

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Settings > Button size scales the bubble and, with it, the docked sliver; Settings >
        // Button opacity is the free-floating alpha, and docked is a fixed fraction of that.
        // Read once per show — the bubble is re-created on every collapse anyway, and Settings
        // itself removes any showing one.
        val config = ConfigStore.load(this)
        val bubbleScale = config.peekBubbleSize
        val bubbleAlpha = config.peekBubbleAlpha
        val dockAlpha = bubbleAlpha * DOCK_ALPHA_FACTOR

        // See GearOverlayService.activeDisplayContext for why this isn't just DEFAULT_DISPLAY.
        val overlayContext = activeDisplayContext()
        val density = overlayContext.resources.displayMetrics.density
        val sizePx = (BUBBLE_DP * bubbleScale * density).toInt()
        val marginPx = (MARGIN_DP * density).toInt()
        val tapSlopPx = (TAP_SLOP_DP * density)
        val trashSizePx = (TRASH_SIZE_DP * density).toInt()
        val trashActivateRadiusPx = TRASH_ACTIVATE_RADIUS_DP * density
        val dockZonePx = (DOCK_ZONE_DP * density).toInt()
        val dockPeekPx = (sizePx * config.peekBubbleDockPeek).toInt().coerceAtLeast(1)

        val wm = overlayContext.getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val screenWidthPx: Int
        val screenHeightPx: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidthPx = bounds.width()
            screenHeightPx = bounds.height()
        } else {
            screenWidthPx = overlayContext.resources.displayMetrics.widthPixels
            screenHeightPx = overlayContext.resources.displayMetrics.heightPixels
        }
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0)
        // Window X when tucked into an edge — mostly off screen (FLAG_LAYOUT_NO_LIMITS allows
        // it), with dockPeekPx of it left showing.
        fun dockedX(side: Int): Int = if (side < 0) dockPeekPx - sizePx else screenWidthPx - dockPeekPx

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var dock = prefs.getInt(KEY_PEEK_DOCK, 0).coerceIn(-1, 1)
        val defaultX = maxX - marginPx
        val defaultY = maxY - marginPx * 3 // a bit above the very bottom edge, clear of gesture nav
        val startX = if (dock != 0) dockedX(dock) else prefs.getInt(KEY_PEEK_X, defaultX).coerceIn(0, maxX)
        val startY = prefs.getInt(KEY_PEEK_Y, defaultY).coerceIn(0, maxY)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val view = roundIconView(overlayContext, sizePx, R.drawable.ic_list_bubble, 0xCC3C4043.toInt()).apply {
            contentDescription = context.getString(R.string.quick_pick_reopen_desc)
            alpha = bubbleAlpha
            if (dock != 0) {
                alpha = dockAlpha
                scaleX = DOCK_SCALE
                scaleY = DOCK_SCALE
            }
        }

        // Drop target for drag-to-remove, added only while an actual drag is in progress
        // (not for a plain tap) — bottom-center, same spot Android's own "remove" targets use.
        val trashParams = WindowManager.LayoutParams(
            trashSizePx,
            trashSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (TRASH_BOTTOM_MARGIN_DP * density).toInt()
        }
        // Read back from the actual attached view instead of computing it from the Gravity/
        // offset math ourselves — a real Window's resolved on-screen position is the ground
        // truth; deriving it independently is an easy way to end up off by enough that the
        // drop point silently never registers as "over the trash".
        val trashLocation = IntArray(2)
        fun trashCenter(): Pair<Float, Float>? {
            val t = trashView ?: return null
            t.getLocationOnScreen(trashLocation)
            return (trashLocation[0] + trashSizePx / 2f) to (trashLocation[1] + trashSizePx / 2f)
        }

        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0
        var dragging = false
        var overTrash = false
        val scroller = OverScroller(overlayContext).apply { setFriction(FLING_FRICTION) }
        val maxFlingVelocityPx = MAX_FLING_VELOCITY_DP_PER_S * density
        var velocityTracker: VelocityTracker? = null
        var animator: ValueAnimator? = null
        val easeOut = PathInterpolator(0.2f, 0f, 0f, 1f)

        fun removeTrashView() {
            trashView?.let { runCatching { wm.removeView(it) } }
            trashView = null
        }

        fun persistPosition() {
            prefs.edit()
                .putInt(KEY_PEEK_X, params.x.coerceIn(0, maxX))
                .putInt(KEY_PEEK_Y, params.y)
                .putInt(KEY_PEEK_DOCK, dock)
                .apply()
        }

        // One animation for position (window params) and look (view alpha/scale) together —
        // the window can't be animated by the view system, so it's driven by hand.
        fun animateTo(targetX: Int, targetY: Int, targetAlpha: Float, targetScale: Float, durationMs: Long, onEnd: () -> Unit) {
            animator?.cancel()
            val fromX = params.x
            val fromY = params.y
            val fromAlpha = view.alpha
            val fromScale = view.scaleX
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = easeOut
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    params.x = (fromX + (targetX - fromX) * t).roundToInt()
                    params.y = (fromY + (targetY - fromY) * t).roundToInt()
                    view.alpha = fromAlpha + (targetAlpha - fromAlpha) * t
                    val s = fromScale + (targetScale - fromScale) * t
                    view.scaleX = s
                    view.scaleY = s
                    runCatching { wm.updateViewLayout(view, params) }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) onEnd() }
                })
                start()
            }
        }

        // Real momentum instead of a forced snap: whatever speed the finger was moving at
        // release keeps carrying the bubble, decelerating via the same friction curve
        // Android uses for scroll flings, until it comes to rest on its own — wherever
        // that happens to be, clamped to the screen. Unless that resting point is against a
        // side edge (a throw at the edge lands there too): then, in one continuous motion,
        // it tucks into the edge instead — see the class comment.
        fun settle(velocityX: Int, velocityY: Int) {
            scroller.forceFinished(true)
            scroller.fling(params.x, params.y, velocityX, velocityY, 0, maxX, 0, maxY)
            val restX = scroller.finalX
            val restY = scroller.finalY
            val side = when {
                restX <= dockZonePx -> -1
                restX >= maxX - dockZonePx -> 1
                else -> 0
            }
            if (side != 0) {
                scroller.forceFinished(true)
                dock = side
                animateTo(dockedX(side), restY, dockAlpha, DOCK_SCALE, DOCK_ANIM_MS) { persistPosition() }
                return
            }
            dock = 0
            fun step() {
                if (scroller.computeScrollOffset()) {
                    params.x = scroller.currX
                    params.y = scroller.currY
                    runCatching { wm.updateViewLayout(view, params) }
                    view.postOnAnimation(::step)
                } else {
                    persistPosition()
                }
            }
            step()
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animator?.cancel()
                    scroller.forceFinished(true)
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = params.x
                    downParamY = params.y
                    dragging = false
                    overTrash = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > tapSlopPx || abs(dy) > tapSlopPx)) {
                        dragging = true
                        if (dock != 0) {
                            // Pulling it out of the edge: back to full size and opacity, and
                            // re-based so it lands fully on screen and follows the finger 1:1
                            // from there (rather than only catching up once the finger has
                            // travelled the hidden width).
                            dock = 0
                            downParamX = params.x.coerceIn(0, maxX) - dx.roundToInt()
                            view.animate().alpha(bubbleAlpha).scaleX(1f).scaleY(1f).setDuration(UNDOCK_ANIM_MS).start()
                        }
                        if (trashView == null) {
                            val newTrash = roundIconView(overlayContext, trashSizePx, R.drawable.ic_close_bubble, 0xE6D32F2F.toInt()).apply {
                                contentDescription = context.getString(R.string.quick_pick_remove_desc)
                            }
                            runCatching { wm.addView(newTrash, trashParams) }.onSuccess { trashView = newTrash }
                        }
                    }
                    if (dragging) {
                        params.x = (downParamX + dx).roundToInt().coerceIn(0, maxX)
                        params.y = (downParamY + dy).roundToInt().coerceIn(0, maxY)
                        runCatching { wm.updateViewLayout(view, params) }

                        val bubbleCenterX = params.x + sizePx / 2f
                        val bubbleCenterY = params.y + sizePx / 2f
                        val (trashCx, trashCy) = trashCenter() ?: (Float.NaN to Float.NaN)
                        val nowOverTrash = !trashCx.isNaN() &&
                            hypot((bubbleCenterX - trashCx).toDouble(), (bubbleCenterY - trashCy).toDouble()) < trashActivateRadiusPx
                        if (nowOverTrash != overTrash) {
                            overTrash = nowOverTrash
                            view.animate().scaleX(if (overTrash) 0.7f else 1f).scaleY(if (overTrash) 0.7f else 1f)
                                .setDuration(120).start()
                            trashView?.animate()?.scaleX(if (overTrash) 1.25f else 1f)?.scaleY(if (overTrash) 1.25f else 1f)
                                ?.setDuration(120)?.start()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    if (dragging) {
                        removeTrashView()
                        if (overTrash) {
                            // Dragging to the trash is an explicit "stop showing this". By default
                            // that's persisted (or the bubble just comes back on the next minimize
                            // and the removal reads as broken); with peekBubbleReturns on, it's
                            // only gone until the next launch collapses the list again.
                            runCatching {
                                val config = ConfigStore.load(this)
                                if (!config.peekBubbleReturns) ConfigStore.save(this, config.copy(showPeekBubble = false))
                            }
                            stopSelf()
                        } else {
                            velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocityPx)
                            settle(
                                velocityTracker?.xVelocity?.roundToInt() ?: 0,
                                velocityTracker?.yVelocity?.roundToInt() ?: 0
                            )
                        }
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        startActivity(
                            Intent(this, QuickPickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        stopSelf()
                    }
                    velocityTracker?.recycle()
                    velocityTracker = null
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(view, params) }.onFailure { stopSelf(); return START_NOT_STICKY }
        bubbleView = view
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bubbleView?.let { v -> runCatching { windowManager?.removeView(v) } }
        bubbleView = null
        trashView?.let { v -> runCatching { windowManager?.removeView(v) } }
        trashView = null
        super.onDestroy()
    }

    companion object {
        /**
         * Escape hatch in case the bubble ever ends up somewhere the user can't get back to
         * (e.g. left stranded after a display/orientation change while showing): forgets the
         * saved position and docking — the next bubble starts fresh at the default corner —
         * and removes any bubble showing right now. Called on entering and leaving Settings.
         */
        fun resetSavedPosition(context: Context) {
            context.stopService(Intent(context, QuickPickPeekOverlayService::class.java))
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PEEK_X)
                .remove(KEY_PEEK_Y)
                .remove(KEY_PEEK_DOCK)
                .apply()
        }
    }
}
