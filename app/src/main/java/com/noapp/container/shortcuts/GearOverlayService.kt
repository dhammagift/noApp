package com.noapp.container.shortcuts

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.noapp.container.MainActivity
import com.noapp.container.R

private const val DISPLAY_MS = 2500L
private const val ICON_DP = 40
private const val TOP_MARGIN_DP = 12
private const val END_MARGIN_DP = 12

/**
 * Flashes a translucent Configure gear over whatever was just launched, for
 * useAllSlotsInDirectMode: dispatch itself stays instant (see MainActivity's
 * dispatchIfShortcut), this only adds a tappable overlay on top, auto-dismissing.
 * Never started unless Settings.canDrawOverlays() is already true — that's gated at
 * the point the Settings toggle is turned on.
 */
class GearOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoRemove = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView != null) {
            handler.removeCallbacks(autoRemove)
            handler.postDelayed(autoRemove, DISPLAY_MS)
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val density = resources.displayMetrics.density
        val sizePx = (ICON_DP * density).toInt()

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // wm.currentWindowMetrics (API 30+) reports the bounds/insets of the display this
        // exact WindowManager instance is actually attached to — resources.displayMetrics is
        // process-wide and can be stale or simply wrong for the currently-active display on a
        // foldable (e.g. still reflecting the main screen while the cover/outer display, a much
        // smaller square panel, is what's actually on). Below API 30 there's no per-display
        // metrics API for a Service, so fall back to the old resource-lookup heuristic.
        val statusBarPx: Int
        val screenWidthPx: Int
        val screenHeightPx: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            screenWidthPx = bounds.width()
            screenHeightPx = bounds.height()
            statusBarPx = metrics.windowInsets
                .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                .top
        } else {
            screenWidthPx = resources.displayMetrics.widthPixels
            screenHeightPx = resources.displayMetrics.heightPixels
            statusBarPx = resources.getIdentifier("status_bar_height", "dimen", "android")
                .takeIf { it > 0 }
                ?.let { resources.getDimensionPixelSize(it) }
                ?: (24 * density).toInt()
        }
        // Still clamp even with real metrics — a cover display can report a status-bar inset
        // disproportionately large relative to its own small size, e.g. via a shared system
        // value — so the icon must never be allowed past a fifth of the shorter screen edge.
        val maxYPx = (minOf(screenHeightPx, screenWidthPx) * 0.2f).toInt()

        val view = ImageView(this).apply {
            val gear = ContextCompat.getDrawable(this@GearOverlayService, R.drawable.ic_settings_gear)
            setImageBitmap(gear?.toBitmap(sizePx, sizePx))
            alpha = 0.55f
            setOnClickListener {
                startActivity(
                    Intent(this@GearOverlayService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_OPEN_CONFIG, true)
                )
                stopSelf()
            }
        }
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (END_MARGIN_DP * density).toInt().coerceAtMost((screenWidthPx - sizePx).coerceAtLeast(0))
            // Clear the status bar / notification shade, not just a fixed margin from the
            // raw screen edge — otherwise it renders half-hidden underneath it. Clamped so a
            // tiny cover display's disproportionate status-bar-height lookup can't push it
            // past the visible area.
            y = (statusBarPx + (TOP_MARGIN_DP * density).toInt()).coerceAtMost(maxYPx)
        }

        runCatching { wm.addView(view, params) }.onFailure { stopSelf(); return START_NOT_STICKY }
        overlayView = view
        handler.postDelayed(autoRemove, DISPLAY_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
