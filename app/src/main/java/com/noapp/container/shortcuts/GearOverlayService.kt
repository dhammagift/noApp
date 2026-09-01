package com.noapp.container.shortcuts

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.noapp.container.MainActivity
import com.noapp.container.icon.monogramBitmap

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
        val statusBarPx = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }
            ?.let { resources.getDimensionPixelSize(it) }
            ?: (24 * density).toInt()
        val sizePx = (ICON_DP * density).toInt()

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = ImageView(this).apply {
            setImageBitmap(monogramBitmap("⚙", "#3C4043", sizePx))
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
            x = (END_MARGIN_DP * density).toInt()
            // Clear the status bar / notification shade, not just a fixed margin from the
            // raw screen edge — otherwise it renders half-hidden underneath it.
            y = statusBarPx + (TOP_MARGIN_DP * density).toInt()
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
