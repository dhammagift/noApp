package com.noapp.container.shortcuts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Display
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
// Matches the Settings gear glyph's actual on-screen size (Material's default 24dp Icon,
// as seen in ConfigScreen's own Settings button) — the 40dp box this used to render at
// filled that whole area edge-to-edge once it became a solid vector glyph instead of a
// smaller emoji-in-circle, which read as oversized. Kept unchanged; only the scrim behind
// it (see SCRIM_DP) grew, so the glyph itself still reads at the same tuned size.
private const val ICON_DP = 24
// The window/view's own bounds — a few dp larger than the glyph so the dark scrim behind it
// (added for contrast over light-themed apps, where the glyph's flat white vector alone would
// be invisible) shows as a visible ring instead of being clipped flush to the glyph's edges.
private const val SCRIM_DP = 32
private const val TOP_MARGIN_DP = 12
private const val END_MARGIN_DP = 12

/**
 * Flashes a tappable Configure gear over whatever Direct mode just launched — dispatch itself
 * stays instant (see MainActivity's dispatchIfShortcut), this only adds the overlay on top,
 * auto-dismissing. Shown on every Direct-mode dispatch, not just when
 * useAllSlotsInDirectMode has removed the long-press Configure entry — see dispatchIfShortcut's
 * own comment for why unconditional. Never started unless Settings.canDrawOverlays() is already
 * true; the mode picker and the "Use all shortcut slots" toggle both actively ask for that
 * permission (see ConfigScreen's GearOverlayPermissionDialog use and SettingsScreen).
 */
class GearOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoRemove = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    /** See the comment at its call site in [onStartCommand] for why this exists. */
    private fun activeDisplayContext(): Context {
        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager ?: return this
        val active = runCatching { displayManager.displays.firstOrNull { it.state == Display.STATE_ON } }
            .getOrNull() ?: return this
        return runCatching { createDisplayContext(active) }.getOrDefault(this)
    }

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

        // getSystemService(WINDOW_SERVICE) on a plain Service (no Activity/Display of its own)
        // always attaches to Display.DEFAULT_DISPLAY — normally fine, but on a folded flip
        // phone the OS switches rendering to the small cover display while the *internal*
        // display (still id/DEFAULT_DISPLAY on many OEM builds) is powered off. A window added
        // there is on a screen nobody is looking at: not mispositioned, just literally never
        // shown. createDisplayContext() on whichever display DisplayManager reports as
        // currently STATE_ON routes the overlay to the screen actually in front of the user.
        val overlayContext = activeDisplayContext()
        val density = overlayContext.resources.displayMetrics.density
        val iconPx = (ICON_DP * density).toInt()
        val sizePx = (SCRIM_DP * density).toInt()

        val wm = overlayContext.getSystemService(WINDOW_SERVICE) as WindowManager
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
            screenWidthPx = overlayContext.resources.displayMetrics.widthPixels
            screenHeightPx = overlayContext.resources.displayMetrics.heightPixels
            statusBarPx = overlayContext.resources.getIdentifier("status_bar_height", "dimen", "android")
                .takeIf { it > 0 }
                ?.let { overlayContext.resources.getDimensionPixelSize(it) }
                ?: (24 * density).toInt()
        }
        // Still clamp even with real metrics — a cover display can report a status-bar inset
        // disproportionately large relative to its own small size, e.g. via a shared system
        // value — so the icon must never be allowed past a fifth of the shorter screen edge.
        val maxYPx = (minOf(screenHeightPx, screenWidthPx) * 0.2f).toInt()

        // The glyph itself is a flat white vector (see the drawable) — fine over a dark app,
        // invisible over a light one. A dark scrim behind it, not the glyph's own color, is what
        // actually guarantees contrast either way, since it's this overlay's own fixed color
        // rather than whatever the app underneath happens to be.
        val view = ImageView(overlayContext).apply {
            val gear = ContextCompat.getDrawable(overlayContext, R.drawable.ic_settings_gear)
            setImageBitmap(gear?.toBitmap(iconPx, iconPx))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xAA000000.toInt())
            }
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
