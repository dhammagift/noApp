package com.noapp.container.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.noapp.container.MainActivity
import com.noapp.container.R
import com.noapp.container.icon.iconBitmapFor
import com.noapp.container.icon.monogramBitmap
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot

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
 * and getting back to Settings relies on the brief gear shown on each dispatch
 * (see [GearOverlayService]) instead of a long-press entry.
 * AppMode.LIST and AppMode.MIX: a plain tap always shows the full list (which
 * has its own "Configure" row — MIX shows it on top of slot 0's dispatch), so
 * no reserved entry is needed — the whole budget goes to real shortcuts, taken
 * in the user's configured order.
 */
object ShortcutSync {
    fun sync(context: Context, mode: AppMode, slots: List<ShortcutSlot>, useAllSlotsInDirectMode: Boolean = false) {
        val budget = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            .let { if (it <= 0) 4 else it } // defensive; real launchers always report > 0

        val shortcuts = when (mode) {
            AppMode.DIRECT -> {
                val mainConfigured = slots.getOrNull(0)?.isConfigured == true
                val auxSlots = slots.filter { it.id != 0 && it.isConfigured }
                buildList {
                    if (mainConfigured && !useAllSlotsInDirectMode && budget >= 1) add(configureShortcut(context))
                    addAll(auxSlots.take((budget - size).coerceAtLeast(0)).map { shortcutFor(context, it) })
                }
            }
            AppMode.LIST, AppMode.MIX -> {
                slots.filter { it.isConfigured }.take(budget).map { shortcutFor(context, it) }
            }
        }
        // Full replace each time: always under budget by construction, no drift bookkeeping needed.
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun configureShortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, CONFIGURE_SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_configure_label))
            .setIcon(IconCompat.createWithBitmap(monogramBitmap("⚙", "#3C4043", SHORTCUT_ICON_SIZE_PX)))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_OPEN_CONFIG, true)
            )
            .build()

    /** Exposed (not just used internally by [sync]) so Settings can pin a single slot as its own home-screen icon. */
    internal fun shortcutFor(context: Context, slot: ShortcutSlot): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_SLOT_ID, slot.id)

        return ShortcutInfoCompat.Builder(context, "slot_${slot.id}")
            .setShortLabel(slot.label.ifBlank { context.getString(R.string.common_item_n, slot.id + 1) })
            .setIcon(IconCompat.createWithBitmap(iconBitmapFor(context, slot, SHORTCUT_ICON_SIZE_PX)))
            .setIntent(intent)
            .build()
    }
}
