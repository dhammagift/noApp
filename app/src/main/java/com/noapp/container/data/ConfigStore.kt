package com.noapp.container.data

import android.content.Context
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for the 5-slot config: JSON encode/decode is shared
 * verbatim between SharedPreferences persistence and file export/import,
 * so there is exactly one serialization format to keep correct.
 */
object ConfigStore {
    private const val PREFS_NAME = "no_app_prefs"
    private const val KEY_SLOTS_JSON = "slots_json"

    fun toJson(slots: List<ShortcutSlot>): String {
        val arr = JSONArray()
        slots.forEach { slot ->
            arr.put(
                JSONObject().apply {
                    put("id", slot.id)
                    put("type", slot.type?.name ?: JSONObject.NULL)
                    put("label", slot.label)
                    put("color", slot.color)
                    put("param", slot.param)
                    put("customIcon", slot.customIcon)
                }
            )
        }
        return JSONObject().put("slots", arr).toString()
    }

    fun fromJson(json: String): List<ShortcutSlot> {
        val defaults = ShortcutSlot.emptySlots().associateBy { it.id }.toMutableMap()
        runCatching {
            val arr = JSONObject(json).getJSONArray("slots")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optInt("id", i)
                if (id !in 0..4) continue
                val type = if (obj.isNull("type")) null else {
                    runCatching { SlotType.valueOf(obj.getString("type")) }.getOrNull()
                }
                defaults[id] = ShortcutSlot(
                    id = id,
                    type = type,
                    label = obj.optString("label", ""),
                    color = obj.optString("color", ShortcutSlot.DEFAULT_COLOR),
                    param = obj.optString("param", ""),
                    customIcon = obj.optString("customIcon", "")
                )
            }
        }
        return (0..4).map { defaults.getValue(it) }
    }

    fun load(context: Context): List<ShortcutSlot> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SLOTS_JSON, null) ?: return ShortcutSlot.emptySlots()
        return fromJson(json)
    }

    fun save(context: Context, slots: List<ShortcutSlot>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SLOTS_JSON, toJson(slots))
            .apply()
    }
}
