package com.noapp.container.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.noapp.container.MainActivity
import com.noapp.container.R
import com.noapp.container.icon.enabledLauncherComponent
import com.noapp.container.icon.iconBitmapFor
import com.noapp.container.icon.monogramBitmap
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot
import java.util.concurrent.Executors

const val EXTRA_SLOT_ID = "extra_slot_id"
const val EXTRA_OPEN_CONFIG = "extra_open_config"
private const val SHORTCUT_ICON_SIZE_PX = 108
private const val CONFIGURE_SHORTCUT_ID = "configure"

/**
 * Publishes configured slots as dynamic App Shortcuts (long-press menu), always
 * capped at the device's actual shortcut budget regardless of how many slots
 * are configured overall.
 *
 * AppMode.DIRECT: a plain tap bypasses all UI when slot 0 is configured, so a
 * permanent "Configure" entry is reserved here as the only remaining way back
 * into Settings/Config — using up 1 of the budget. Unless [useAllSlotsInDirectMode]
 * opts out of that (Settings toggle): then the whole budget goes to real items,
 * and getting back to Settings relies on the gear shown on each dispatch
 * (see [GearOverlayService]) instead of a long-press entry.
 * AppMode.LIST: a plain tap always shows the full list (which has its own
 * "Configure" row), so no reserved entry is needed — the whole budget goes to
 * real shortcuts, taken in the user's configured order.
 * AppMode.MIX: same as LIST, except slot 0 is excluded — a plain tap already
 * launches it directly (see MainActivity.dispatchIfShortcut), so a long-press
 * shortcut for it too would just duplicate something that happens on tap anyway.
 */
object ShortcutSync {
    // One background thread, so calls still apply in order (last sync wins) while none of the
    // work — up to a dozen app-icon loads through PackageManager plus the ShortcutManager IPC —
    // lands on the main thread, where it used to be part of every cold start and every edit.
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ShortcutSync") }

    // Shortcuts are a nice-to-have, never something allowed to take core app dispatch down
    // with it — this OS integration can fail in ways specific to a given device/launcher that
    // are impossible to fully predict, so every call is defensive, same as the rest of this
    // package (ActionDispatcher, GearOverlayService) already treats its own OS calls.
    fun sync(context: Context, mode: AppMode, slots: List<ShortcutSlot>, useAllSlotsInDirectMode: Boolean = false) {
        val appContext = context.applicationContext
        val snapshot = slots.toList()
        executor.execute {
            runCatching {
                val budget = ShortcutManagerCompat.getMaxShortcutCountPerActivity(appContext)
                    .let { if (it <= 0) 4 else it } // defensive; real launchers always report > 0
                // Without an explicit activity, several launchers silently show no shortcuts at all
                // for an app whose actual launcher icon is one of many activity-aliases rather than a
                // plain activity — see enabledLauncherComponent's doc comment.
                val component = enabledLauncherComponent(appContext)

                val shortcuts = when (mode) {
                    AppMode.DIRECT -> {
                        val mainConfigured = snapshot.getOrNull(0)?.isConfigured == true
                        val auxSlots = snapshot.filter { it.id != 0 && it.isConfigured }
                        buildList {
                            if (mainConfigured && !useAllSlotsInDirectMode && budget >= 1) add(configureShortcut(appContext, component))
                            addAll(auxSlots.take((budget - size).coerceAtLeast(0)).map { shortcutFor(appContext, it, component) })
                        }
                    }
                    AppMode.LIST -> {
                        snapshot.filter { it.isConfigured }.take(budget).map { shortcutFor(appContext, it, component) }
                    }
                    AppMode.MIX -> {
                        snapshot.filter { it.id != 0 && it.isConfigured }.take(budget).map { shortcutFor(appContext, it, component) }
                    }
                }
                // Full replace each time: always under budget by construction, no drift bookkeeping needed.
                ShortcutManagerCompat.setDynamicShortcuts(appContext, shortcuts)
            }
        }
    }

    private fun configureShortcut(context: Context, component: ComponentName): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, CONFIGURE_SHORTCUT_ID)
            .setActivity(component)
            .setShortLabel(context.getString(R.string.shortcut_configure_label))
            .setIcon(IconCompat.createWithBitmap(monogramBitmap("⚙", "#3C4043", SHORTCUT_ICON_SIZE_PX)))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_OPEN_CONFIG, true)
            )
            .build()

    /** Exposed (not just used internally by [sync]) so Settings can pin a single slot as its own home-screen icon. */
    internal fun shortcutFor(context: Context, slot: ShortcutSlot, component: ComponentName): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_SLOT_ID, slot.id)

        return ShortcutInfoCompat.Builder(context, "slot_${slot.id}")
            .setActivity(component)
            .setShortLabel(slot.label.ifBlank { context.getString(R.string.common_item_n, slot.id + 1) })
            .setIcon(IconCompat.createWithBitmap(iconBitmapFor(context, slot, SHORTCUT_ICON_SIZE_PX)))
            .setIntent(intent)
            .build()
    }
}
