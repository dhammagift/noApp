package com.noapp.container.model

enum class SlotType { APP, URL, INTENT, CUSTOM }

/**
 * One entry in a variable-length, user-ordered list (see [AppConfig]). [id] is
 * always its current list position, reassigned on every add/remove/reorder —
 * not a stable identity. In AppMode.DIRECT, id 0 is special ("Main": fires on
 * a plain tap); in AppMode.LIST every entry is just a row in the shown list.
 * [param] holds the type-specific payload: package name (APP), URL (URL),
 * or an intent URI string parsed via Intent.parseUri (INTENT, CUSTOM).
 * [customIcon], if set, overrides the icon entirely (an emoji or short text,
 * drawn on a [color] badge) — otherwise APP slots show the target app's own
 * launcher icon and everything else falls back to a monogram of [label].
 */
data class ShortcutSlot(
    val id: Int,
    val type: SlotType? = null,
    val label: String = "",
    val color: String = DEFAULT_COLOR,
    val param: String = "",
    val customIcon: String = ""
) {
    val isConfigured: Boolean get() = type != null && param.isNotBlank()

    companion object {
        const val DEFAULT_COLOR = "#5F6368"
        val PALETTE = listOf("#5F6368", "#1A73E8", "#188038", "#D93025", "#F9AB00", "#8E24AA")
        fun emptySlots(): List<ShortcutSlot> = (0..4).map { ShortcutSlot(id = it) }
    }
}
