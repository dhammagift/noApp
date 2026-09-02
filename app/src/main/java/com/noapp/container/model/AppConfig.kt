package com.noapp.container.model

/**
 * LIST (default): a plain tap always shows the configured slots as an in-app
 * bottom-anchored list, in the user's own order — not tied to the OS shortcut
 * count limit, since it never goes through ShortcutManagerCompat for its main
 * interaction.
 * DIRECT: a plain tap launches slot 0 straight away (no UI), and the rest are
 * OS long-press shortcuts — capped at the device's shortcut budget.
 * MIX: a plain tap launches slot 0 straight away, same as DIRECT, but also
 * shows the same list sheet DIRECT would only get via the gear overlay — always,
 * on top of whatever slot 0 opened. Shares DIRECT's "slot 0 is Main" shape but
 * shares LIST's shortcut budget (no reserved Configure entry needed, since the
 * sheet's own header already has one).
 */
enum class AppMode { LIST, DIRECT, MIX }

data class AppConfig(
    val mode: AppMode = AppMode.LIST,
    val slots: List<ShortcutSlot> = ShortcutSlot.emptySlots(),
    // DIRECT only: skip reserving one OS shortcut slot for "Configure" so all of the
    // device's shortcut budget goes to real items. Trades that permanent long-press
    // entry for a brief tappable gear shown on every plain-tap dispatch instead.
    val useAllSlotsInDirectMode: Boolean = false,
    // Matches an IconVariant.id in icon/AppIconSwitcher.kt — "default" is the plain app icon.
    val iconVariant: String = "default"
)
