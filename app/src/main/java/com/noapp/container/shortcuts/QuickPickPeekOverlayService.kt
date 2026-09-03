package com.noapp.container.shortcuts

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
import android.widget.ImageView
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.noapp.container.QuickPickActivity
import com.noapp.container.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val PREFS_NAME = "no_app_prefs"
private const val KEY_PEEK_X = "peek_bubble_x"
private const val KEY_PEEK_Y = "peek_bubble_y"
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

/**
 * MIX/LIST mode's collapsed-list affordance: a small draggable button drawn as a
 * real system overlay (TYPE_APPLICATION_OVERLAY), not Compose content inside our
 * own Activity window — so it keeps showing over whatever app slot 0 launched (or
 * any other app the user switches to), instead of disappearing the moment
 * QuickPickActivity itself isn't the foreground window. Never auto-dismisses
 * (unlike GearOverlayService): it sits wherever it's dropped until tapped (reopens
 * the list) or dragged onto the red drop target (removes it for good).
 *
 * Only started when Settings.canDrawOverlays() is already true — see the call site
 * in QuickPickSheet.kt, which falls back to an in-Activity peek otherwise.
 *
 * Dragging carries real momentum on release (OverScroller, the same friction-based
 * fling used for scrolling) instead of snapping to a screen edge — the user should be
 * free to leave it wherever they actually let go, just decelerating naturally rather
 * than stopping dead.
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

        // See GearOverlayService.activeDisplayContext for why this isn't just DEFAULT_DISPLAY.
        val overlayContext = activeDisplayContext()
        val density = overlayContext.resources.displayMetrics.density
        val sizePx = (BUBBLE_DP * density).toInt()
        val marginPx = (MARGIN_DP * density).toInt()
        val tapSlopPx = (TAP_SLOP_DP * density)
        val trashSizePx = (TRASH_SIZE_DP * density).toInt()
        val trashActivateRadiusPx = TRASH_ACTIVATE_RADIUS_DP * density

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

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultX = maxX - marginPx
        val defaultY = maxY - marginPx * 3 // a bit above the very bottom edge, clear of gesture nav
        val startX = prefs.getInt(KEY_PEEK_X, defaultX).coerceIn(0, maxX)
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
        val trashCenterX = screenWidthPx / 2f
        val trashCenterY = screenHeightPx - trashParams.y - trashSizePx / 2f

        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0
        var dragging = false
        var overTrash = false
        val scroller = OverScroller(overlayContext).apply { setFriction(FLING_FRICTION) }
        val maxFlingVelocityPx = MAX_FLING_VELOCITY_DP_PER_S * density
        var velocityTracker: VelocityTracker? = null

        fun removeTrashView() {
            trashView?.let { runCatching { wm.removeView(it) } }
            trashView = null
        }

        // Real momentum instead of a forced snap: whatever speed the finger was moving at
        // release keeps carrying the bubble, decelerating via the same friction curve
        // Android uses for scroll flings, until it comes to rest on its own — wherever
        // that happens to be, clamped to the screen but never pulled toward an edge.
        fun flingToRest(velocityX: Int, velocityY: Int) {
            scroller.forceFinished(true)
            scroller.fling(params.x, params.y, velocityX, velocityY, 0, maxX, 0, maxY)
            fun step() {
                if (scroller.computeScrollOffset()) {
                    params.x = scroller.currX
                    params.y = scroller.currY
                    runCatching { wm.updateViewLayout(view, params) }
                    view.postOnAnimation(::step)
                } else {
                    prefs.edit().putInt(KEY_PEEK_X, params.x).putInt(KEY_PEEK_Y, params.y).apply()
                }
            }
            step()
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
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
                        val distanceToTrash = hypot((bubbleCenterX - trashCenterX).toDouble(), (bubbleCenterY - trashCenterY).toDouble())
                        val nowOverTrash = distanceToTrash < trashActivateRadiusPx
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
                            stopSelf()
                        } else {
                            velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocityPx)
                            flingToRest(
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
         * saved position — the next bubble starts fresh at the default corner — and removes
         * any bubble showing right now. Called on entering and leaving Settings.
         */
        fun resetSavedPosition(context: Context) {
            context.stopService(Intent(context, QuickPickPeekOverlayService::class.java))
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PEEK_X)
                .remove(KEY_PEEK_Y)
                .apply()
        }
    }
}
