package com.noapp.container.shortcuts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.noapp.container.QuickPickActivity
import com.noapp.container.R
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREFS_NAME = "no_app_prefs"
private const val KEY_PEEK_X = "peek_bubble_x"
private const val KEY_PEEK_Y = "peek_bubble_y"
private const val BUBBLE_DP = 48
private const val MARGIN_DP = 20
private const val TAP_SLOP_DP = 8

/**
 * MIX mode's collapsed-list affordance: a small draggable button drawn as a real
 * system overlay (TYPE_APPLICATION_OVERLAY), not Compose content inside our own
 * Activity window — so it keeps showing over whatever app slot 0 launched, exactly
 * like it would over any other app the user switches to, instead of disappearing
 * the moment QuickPickActivity itself isn't the foreground window. Never
 * auto-dismisses (unlike GearOverlayService): it's meant to sit wherever it's
 * dragged until tapped, which reopens the list and removes it.
 *
 * Only started when Settings.canDrawOverlays() is already true — see the call site
 * in QuickPickSheet.kt, which falls back to an in-Activity peek otherwise.
 */
class QuickPickPeekOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** See the comment at its call site in [onStartCommand] for why this exists. */
    private fun activeDisplayContext(): Context {
        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager ?: return this
        val active = runCatching { displayManager.displays.firstOrNull { it.state == Display.STATE_ON } }
            .getOrNull() ?: return this
        return runCatching { createDisplayContext(active) }.getOrDefault(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Already showing: leave it exactly where the user dragged it rather than
        // resetting position or stacking a second bubble.
        if (bubbleView != null) return START_NOT_STICKY

        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // See GearOverlayService.activeDisplayContext for why this isn't just DEFAULT_DISPLAY.
        val overlayContext = activeDisplayContext()
        val density = overlayContext.resources.displayMetrics.density
        val sizePx = (BUBBLE_DP * density).toInt()
        val marginPx = (MARGIN_DP * density).toInt()
        val tapSlopPx = (TAP_SLOP_DP * density)

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

        val view = ImageView(overlayContext).apply {
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xCC3C4043.toInt())
            }
            background = bg
            val icon = ContextCompat.getDrawable(overlayContext, R.drawable.ic_list_bubble)
            val iconPad = (sizePx * 0.24f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setImageBitmap(icon?.toBitmap(sizePx - iconPad * 2, sizePx - iconPad * 2))
            contentDescription = context.getString(R.string.quick_pick_reopen_desc)
        }

        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = params.x
                    downParamY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    params.x = (downParamX + dx).roundToInt().coerceIn(0, maxX)
                    params.y = (downParamY + dy).roundToInt().coerceIn(0, maxY)
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - downRawX) > tapSlopPx || abs(event.rawY - downRawY) > tapSlopPx
                    prefs.edit().putInt(KEY_PEEK_X, params.x).putInt(KEY_PEEK_Y, params.y).apply()
                    if (!moved) {
                        startActivity(
                            Intent(this, QuickPickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        stopSelf()
                    }
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
        super.onDestroy()
    }
}
