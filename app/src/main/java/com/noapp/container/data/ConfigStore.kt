package com.noapp.container.data

import android.content.Context
import com.noapp.container.model.AppConfig
import com.noapp.container.model.AppMode
import com.noapp.container.model.AppTheme
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for the config: JSON encode/decode is shared verbatim
 * between SharedPreferences persistence and file export/import, so there is
 * exactly one serialization format to keep correct. The slot list is
 * variable-length — array order IS the id, no separate "id" field on the wire.
 */
object ConfigStore {
    private const val PREFS_NAME = "no_app_prefs"
    private const val KEY_CONFIG_JSON = "config_json"

    fun toJson(config: AppConfig): String {
        val arr = JSONArray()
        config.slots.forEach { slot ->
            arr.put(
                JSONObject().apply {
                    put("type", slot.type?.name ?: JSONObject.NULL)
                    put("label", slot.label)
                    put("color", slot.color)
                    put("param", slot.param)
                    put("customIcon", slot.customIcon)
                }
            )
        }
        return JSONObject()
            .put("mode", config.mode.name)
            .put("slots", arr)
            .put("useAllSlotsInDirectMode", config.useAllSlotsInDirectMode)
            .put("iconVariant", config.iconVariant)
            .put("showPeekBubble", config.showPeekBubble)
            .put("peekBubbleReturns", config.peekBubbleReturns)
            .put("showRecentApps", config.showRecentApps)
            .put("theme", config.theme.name)
            .toString()
    }

    fun fromJson(json: String): AppConfig = runCatching {
        val root = JSONObject(json)
        val mode = runCatching { AppMode.valueOf(root.optString("mode", AppMode.LIST.name)) }
            .getOrDefault(AppMode.LIST)
        val arr = root.getJSONArray("slots")
        val slots = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val type = if (obj.isNull("type")) null else {
                runCatching { SlotType.valueOf(obj.getString("type")) }.getOrNull()
            }
            ShortcutSlot(
                id = i,
                type = type,
                label = obj.optString("label", ""),
                color = obj.optString("color", ShortcutSlot.DEFAULT_COLOR),
                param = obj.optString("param", ""),
                customIcon = obj.optString("customIcon", "")
            )
        }
        AppConfig(
            mode = mode,
            slots = slots.ifEmpty { ShortcutSlot.emptySlots() },
            useAllSlotsInDirectMode = root.optBoolean("useAllSlotsInDirectMode", false),
            iconVariant = root.optString("iconVariant", "default"),
            showPeekBubble = root.optBoolean("showPeekBubble", false),
            peekBubbleReturns = root.optBoolean("peekBubbleReturns", false),
            showRecentApps = root.optBoolean("showRecentApps", false),
            theme = runCatching { AppTheme.valueOf(root.optString("theme", AppTheme.SYSTEM.name)) }
                .getOrDefault(AppTheme.SYSTEM)
        )
    }.getOrDefault(AppConfig())

    fun load(context: Context): AppConfig {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG_JSON, null) ?: return AppConfig()
        return fromJson(json)
    }

    fun save(context: Context, config: AppConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG_JSON, toJson(config))
            .apply()
    }
}
