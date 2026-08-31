package com.noapp.container

import com.noapp.container.data.ConfigStore
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigStoreTest {

    @Test
    fun `round trip preserves configured and empty slots`() {
        val slots = listOf(
            ShortcutSlot(id = 0, type = SlotType.APP, label = "Camera", color = "#1A73E8", param = "com.android.camera2"),
            ShortcutSlot(id = 1, type = SlotType.URL, label = "Wiki", color = "#188038", param = "https://wikipedia.org/wiki/{{word}}"),
            ShortcutSlot(id = 2, type = SlotType.INTENT, label = "Dial", color = "#D93025", param = "intent://call#Intent;end"),
            ShortcutSlot(id = 3, type = SlotType.URL, label = "Rocket", color = "#F9AB00", param = "https://example.com", customIcon = "🚀"),
            ShortcutSlot(id = 4) // unconfigured
        )

        val restored = ConfigStore.fromJson(ConfigStore.toJson(slots))

        assertEquals(slots, restored)
    }

    @Test
    fun `malformed json falls back to 5 empty slots`() {
        val restored = ConfigStore.fromJson("not json")
        assertEquals(ShortcutSlot.emptySlots(), restored)
    }
}
