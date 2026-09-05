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

/** Settings > Theme. SYSTEM follows the OS dark-mode setting; the other two force it. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

data class AppConfig(
    val mode: AppMode = AppMode.LIST,
    val slots: List<ShortcutSlot> = ShortcutSlot.emptySlots(),
    // DIRECT only: skip reserving one OS shortcut slot for "Configure" so all of the
    // device's shortcut budget goes to real items. Trades that permanent long-press
    // entry for a brief tappable gear shown on every plain-tap dispatch instead.
    val useAllSlotsInDirectMode: Boolean = false,
    // Matches an IconVariant.id in icon/AppIconSwitcher.kt — "default" is the plain app icon.
    val iconVariant: String = "default",
    // LIST/MIX only, off by default (needs the user to separately grant the "draw over other
    // apps" permission — same reasoning as showRecentApps below): swiping the list away
    // collapses it into a draggable floating button (see QuickPickPeekOverlayService) instead
    // of just closing it. A fresh install starts in LIST mode already, so if this defaulted to
    // on, the permission-request flow (which only fires on an explicit mode *change* — see
    // ConfigScreen's ModePickerDialog) would never run and the toggle would just silently do
    // nothing until the user noticed and re-toggled it themselves.
    val showPeekBubble: Boolean = false,
    // Only meaningful with showPeekBubble: dragging the floating button onto the ✕ target
    // normally turns showPeekBubble off for good (see QuickPickPeekOverlayService); with this
    // on, it's only gone until the next launch collapses the list again.
    val peekBubbleReturns: Boolean = false,
    // Floating button size, as a multiplier of its default 48dp — also scales how much of it
    // stays visible when tucked into a screen edge (see QuickPickPeekOverlayService).
    val peekBubbleSize: Float = 1f,
    // Opacity of the floating button while tucked into a screen edge.
    val peekBubbleDockAlpha: Float = 0.55f,
    // LIST/MIX only, off by default (needs the user to separately grant Usage Access):
    // shows a compact icon-only row of recently-used apps above the configured items —
    // see recents/RecentApps.kt.
    val showRecentApps: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM
)
