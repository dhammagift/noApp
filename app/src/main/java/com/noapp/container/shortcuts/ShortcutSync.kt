package com.noapp.container.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.noapp.container.MainActivity
import com.noapp.container.icon.iconBitmapFor
import com.noapp.container.icon.monogramBitmap
import com.noapp.container.model.ShortcutSlot

const val EXTRA_SLOT_ID = "extra_slot_id"
const val EXTRA_OPEN_CONFIG = "extra_open_config"
private const val SHORTCUT_ICON_SIZE_PX = 108
private const val CONFIGURE_SHORTCUT_ID = "configure"

/**
 * Publishes the auxiliary slots (id 1..4) as dynamic App Shortcuts (long-press menu).
 * When the main slot (id 0) is configured, a plain tap launches it directly instead of
 * opening the app's UI, so a permanent "Configure" entry is reserved here as the only
 * remaining way back into Settings/Config — using up 1 of the device's shortcut budget.
 */
object ShortcutSync {
    fun sync(context: Context, slots: List<ShortcutSlot>) {
        val budget = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            .let { if (it <= 0) 4 else it } // defensive; real launchers always report > 0
        val mainConfigured = slots.getOrNull(0)?.isConfigured == true
        val auxSlots = slots.filter { it.id != 0 && it.isConfigured }

        val shortcuts = buildList {
            if (mainConfigured && budget >= 1) add(configureShortcut(context))
            addAll(auxSlots.take((budget - size).coerceAtLeast(0)).map { shortcutFor(context, it) })
        }
        // Full replace each time: always under budget by construction, no drift bookkeeping needed.
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun configureShortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, CONFIGURE_SHORTCUT_ID)
            .setShortLabel("Configure")
            .setIcon(IconCompat.createWithBitmap(monogramBitmap("⚙", "#3C4043", SHORTCUT_ICON_SIZE_PX)))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_OPEN_CONFIG, true)
            )
            .build()

    private fun shortcutFor(context: Context, slot: ShortcutSlot): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_SLOT_ID, slot.id)

        return ShortcutInfoCompat.Builder(context, "slot_${slot.id}")
            .setShortLabel(slot.label.ifBlank { "Slot ${slot.id}" }) // ids 1..4 already read naturally
            .setIcon(IconCompat.createWithBitmap(iconBitmapFor(context, slot, SHORTCUT_ICON_SIZE_PX)))
            .setIntent(intent)
            .build()
    }
}
