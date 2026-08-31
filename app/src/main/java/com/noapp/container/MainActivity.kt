package com.noapp.container

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.noapp.container.data.ConfigStore
import com.noapp.container.model.AppConfig
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.shortcuts.ActionDispatcher
import com.noapp.container.shortcuts.EXTRA_OPEN_CONFIG
import com.noapp.container.shortcuts.EXTRA_SLOT_ID
import com.noapp.container.shortcuts.ShortcutSync
import com.noapp.container.ui.ConfigScreen
import com.noapp.container.ui.QuickPickSheet
import com.noapp.container.ui.SettingsScreen
import com.noapp.container.ui.SlotEditScreen
import com.noapp.container.ui.theme.NoAppTheme

/**
 * No back stack, no navigation-compose: 4 screens, switched by a single sealed state.
 */
private sealed class Screen {
    data object Config : Screen()
    data class EditSlot(val index: Int) : Screen()
    data object Settings : Screen()
    data class QuickPick(val sharedText: String?) : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (dispatchIfShortcut(intent)) return

        setContent {
            NoAppTheme {
                val initialConfig = remember { ConfigStore.load(this) }
                var mode by remember { mutableStateOf(initialConfig.mode) }
                val slots = remember { mutableStateListOf(*initialConfig.slots.toTypedArray()) }
                var screen by remember { mutableStateOf(startScreen(intent, initialConfig)) }

                fun persist() {
                    val config = AppConfig(mode, slots.toList())
                    ConfigStore.save(this, config)
                    ShortcutSync.sync(this, mode, slots.toList())
                }

                NoAppRoot(
                    mode = mode,
                    slots = slots,
                    screen = screen,
                    onScreenChange = { screen = it },
                    onSlotsChanged = { updated ->
                        slots.clear()
                        slots.addAll(updated)
                        persist()
                    },
                    onModeChanged = { newMode ->
                        mode = newMode
                        persist()
                    },
                    onConfigImported = { imported ->
                        mode = imported.mode
                        slots.clear()
                        slots.addAll(imported.slots)
                        persist()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchIfShortcut(intent)
        // A repeat share/tap while the UI is already open is rare enough to just leave the
        // current screen as-is rather than re-plumb intent state into the composition.
    }

    /** Returns true (and finishes the activity) if [intent] should dispatch straight to a target. */
    private fun dispatchIfShortcut(intent: Intent): Boolean {
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) return false // Configure entry: show UI instead

        val config = ConfigStore.load(this)
        val explicitId = intent.getIntExtra(EXTRA_SLOT_ID, -1)
        val slotId = when {
            explicitId >= 0 -> explicitId
            config.mode == AppMode.DIRECT && isPlainLauncherTap(intent) && config.slots.getOrNull(0)?.isConfigured == true -> 0
            else -> -1
        }
        if (slotId < 0) return false

        config.slots.getOrNull(slotId)?.let { ActionDispatcher.execute(this, it) }
        finish()
        return true
    }

    /**
     * MainActivity only has two real entry points — a launcher tap and an ACTION_SEND share —
     * plus internal shortcut/configure Intents already handled above. "Not a share" is a more
     * robust plain-tap signal than requiring an exact ACTION_MAIN/CATEGORY_LAUNCHER match, since
     * some OEM launchers don't deliver that combo exactly.
     */
    private fun isPlainLauncherTap(intent: Intent): Boolean =
        intent.action != Intent.ACTION_SEND

    private fun startScreen(intent: Intent, config: AppConfig): Screen {
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) return Screen.Config
        val sharedText = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
        return if (config.slots.none { it.isConfigured }) Screen.Config else Screen.QuickPick(sharedText)
    }
}

@androidx.compose.runtime.Composable
private fun NoAppRoot(
    mode: AppMode,
    slots: androidx.compose.runtime.snapshots.SnapshotStateList<ShortcutSlot>,
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    onSlotsChanged: (List<ShortcutSlot>) -> Unit,
    onModeChanged: (AppMode) -> Unit,
    onConfigImported: (AppConfig) -> Unit
) {
    if (screen !is Screen.Config) {
        BackHandler { onScreenChange(Screen.Config) }
    }

    when (screen) {
        is Screen.Config -> ConfigScreen(
            mode = mode,
            slots = slots,
            onEditSlot = { index -> onScreenChange(Screen.EditSlot(index)) },
            onOpenSettings = { onScreenChange(Screen.Settings) },
            onModeChanged = onModeChanged,
            onSlotsChanged = onSlotsChanged
        )

        is Screen.EditSlot -> SlotEditScreen(
            mode = mode,
            slot = slots[screen.index],
            onSave = { updated ->
                val next = slots.toMutableList().also { it[screen.index] = updated }
                onSlotsChanged(next)
                onScreenChange(Screen.Config)
            },
            onCancel = { onScreenChange(Screen.Config) }
        )

        is Screen.Settings -> SettingsScreen(
            config = AppConfig(mode, slots.toList()),
            onImportConfig = onConfigImported,
            onBack = { onScreenChange(Screen.Config) }
        )

        is Screen.QuickPick -> QuickPickSheet(
            slots = slots.filter { it.isConfigured },
            sharedText = screen.sharedText,
            onConfigure = { onScreenChange(Screen.Config) },
            onDismiss = { onScreenChange(Screen.Config) }
        )
    }
}
