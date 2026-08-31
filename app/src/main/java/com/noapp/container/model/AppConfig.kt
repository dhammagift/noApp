package com.noapp.container.model

/**
 * LIST (default): a plain tap always shows the configured slots as an in-app
 * bottom-anchored list, in the user's own order — not tied to the OS shortcut
 * count limit, since it never goes through ShortcutManagerCompat for its main
 * interaction.
 * DIRECT: a plain tap launches slot 0 straight away (no UI), and the rest are
 * OS long-press shortcuts — capped at the device's shortcut budget.
 */
enum class AppMode { LIST, DIRECT }

data class AppConfig(
    val mode: AppMode = AppMode.LIST,
    val slots: List<ShortcutSlot> = ShortcutSlot.emptySlots(),
    // DIRECT only: skip reserving one OS shortcut slot for "Configure" so all of the
    // device's shortcut budget goes to real items. Trades that permanent long-press
    // entry for a brief tappable gear shown on every plain-tap dispatch instead.
    val useAllSlotsInDirectMode: Boolean = false
)
