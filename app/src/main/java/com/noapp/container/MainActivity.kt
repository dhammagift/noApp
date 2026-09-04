package com.noapp.container

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import com.noapp.container.model.SlotType
import com.noapp.container.shortcuts.ActionDispatcher
import com.noapp.container.shortcuts.EXTRA_OPEN_CONFIG
import com.noapp.container.shortcuts.EXTRA_SLOT_ID
import com.noapp.container.shortcuts.GearOverlayService
import com.noapp.container.shortcuts.ShortcutSync
import com.noapp.container.ui.ConfigScreen
import com.noapp.container.ui.CrashReportScreen
import com.noapp.container.ui.SettingsScreen
import com.noapp.container.ui.SlotEditScreen
import com.noapp.container.ui.theme.NoAppTheme

/**
 * No back stack, no navigation-compose: switched by a single sealed state. The
 * plain-tap LIST/share picker lives in QuickPickActivity instead, not here.
 */
private sealed class Screen {
    data object Config : Screen()
    data class EditSlot(val index: Int) : Screen()
    data class NewSlot(val type: SlotType) : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    // Hoisted out of setContent (rather than a plain `remember`) so onNewIntent can navigate
    // back to Config below without needing a reference into the running composition.
    private var screen: Screen by mutableStateOf(Screen.Config)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lastCrash = CrashLogger.readLastCrash(this)
        if (lastCrash != null) {
            // Show the crash instead of the normal screen on the very next launch, so it can be
            // copied straight off the phone — see CrashLogger. Once dismissed, recreate() to run
            // the rest of onCreate fresh, same as any other cold start.
            setContent { NoAppTheme { CrashReportScreen(lastCrash, onDismiss = { CrashLogger.clear(this); recreate() }) } }
            return
        }

        val initialConfig = ConfigStore.load(this)
        // Reconciles the enabled launcher-alias pair with the persisted (variant, mode) —
        // covers an app update that added the "*List" aliases after this config was last
        // saved, or any other drift; a no-op the rest of the time. Doesn't affect this launch,
        // only the next one.
        com.noapp.container.icon.applyLauncherComponent(this, initialConfig.iconVariant, initialConfig.mode)
        if (dispatchIfShortcut(intent, initialConfig)) return

        // Self-heals installs whose shortcuts were published before ShortcutSync started
        // pinning them to the enabled alias explicitly (see its own doc comment) — those
        // never show up in the long-press menu at all until re-synced, and nothing else in
        // this app calls sync() except an actual edit. Only reached once dispatchIfShortcut
        // has already decided this isn't a fast dispatch, so it never adds work (or risk) to
        // that hot path.
        ShortcutSync.sync(this, initialConfig.mode, initialConfig.slots, initialConfig.iconVariant, initialConfig.useAllSlotsInDirectMode)

        setContent {
            NoAppTheme {
                var mode by remember { mutableStateOf(initialConfig.mode) }
                val slots = remember { mutableStateListOf(*initialConfig.slots.toTypedArray()) }
                var useAllSlotsInDirectMode by remember { mutableStateOf(initialConfig.useAllSlotsInDirectMode) }
                var iconVariant by remember { mutableStateOf(initialConfig.iconVariant) }
                var showPeekBubble by remember { mutableStateOf(initialConfig.showPeekBubble) }
                var showRecentApps by remember { mutableStateOf(initialConfig.showRecentApps) }

                fun persist() {
                    val config = AppConfig(mode, slots.toList(), useAllSlotsInDirectMode, iconVariant, showPeekBubble, showRecentApps)
                    ConfigStore.save(this, config)
                    // Must enable the target launcher-alias component before syncing shortcuts to
                    // it (see onCreate's self-heal call and enabledLauncherComponent's doc comment)
                    // — syncing first ties shortcuts to a component that may still be disabled,
                    // an ordering some launchers don't recover from without a reboot or a later re-sync.
                    com.noapp.container.icon.applyLauncherComponent(this, iconVariant, mode)
                    ShortcutSync.sync(this, mode, slots.toList(), iconVariant, useAllSlotsInDirectMode)
                }

                NoAppRoot(
                    mode = mode,
                    slots = slots,
                    useAllSlotsInDirectMode = useAllSlotsInDirectMode,
                    iconVariant = iconVariant,
                    showPeekBubble = showPeekBubble,
                    showRecentApps = showRecentApps,
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
                    onUseAllSlotsInDirectModeChanged = { value ->
                        useAllSlotsInDirectMode = value
                        persist()
                    },
                    onIconVariantChanged = { value ->
                        iconVariant = value
                        persist()
                    },
                    onShowPeekBubbleChanged = { value ->
                        showPeekBubble = value
                        persist()
                    },
                    onShowRecentAppsChanged = { value ->
                        showRecentApps = value
                        persist()
                    },
                    onConfigImported = { imported ->
                        mode = imported.mode
                        slots.clear()
                        slots.addAll(imported.slots)
                        useAllSlotsInDirectMode = imported.useAllSlotsInDirectMode
                        iconVariant = imported.iconVariant
                        showPeekBubble = imported.showPeekBubble
                        showRecentApps = imported.showRecentApps
                        persist()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) {
            // QuickPickActivity redirects here with this extra when there's nothing configured
            // yet to show — e.g. this instance was already running in the background on some
            // other screen. Navigate to Config so the user actually lands somewhere they can
            // add a shortcut, instead of just resurfacing whatever screen was left open.
            screen = Screen.Config
        } else {
            dispatchIfShortcut(intent, ConfigStore.load(this))
            // A repeat share/tap while the UI is already open is rare enough to just leave the
            // current screen as-is rather than re-plumb intent state into the composition.
        }
    }

    /**
     * Returns true (and finishes the activity) if [intent] should be handled without ever
     * showing MainActivity's own UI: an explicit shortcut tap or a plain DIRECT/MIX-mode tap
     * dispatch straight to slot 0 (always instant — [AppConfig.useAllSlotsInDirectMode] only
     * decides whether a translucent Configure gear also flashes on top via
     * [GearOverlayService], DIRECT only); MIX additionally opens the same [QuickPickActivity]
     * sheet LIST uses, on top of whatever slot 0 just launched; a plain LIST-mode tap, a MIX
     * tap with slot 0 unconfigured, or an incoming share also opens that translucent sheet so
     * it overlays whatever was on screen.
     */
    private fun dispatchIfShortcut(intent: Intent, config: AppConfig): Boolean {
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) return false // Configure entry: show UI instead

        val explicitId = intent.getIntExtra(EXTRA_SLOT_ID, -1)
        if (explicitId >= 0) {
            config.slots.getOrNull(explicitId)?.let { ActionDispatcher.execute(this, it) }
            finish()
            return true
        }

        val isPlainTap = isPlainLauncherTap(intent)
        val isPlainMainTap = (config.mode == AppMode.DIRECT || config.mode == AppMode.MIX) &&
            isPlainTap &&
            config.slots.getOrNull(0)?.isConfigured == true
        if (isPlainMainTap) {
            config.slots.getOrNull(0)?.let { ActionDispatcher.execute(this, it) }
            when (config.mode) {
                AppMode.DIRECT -> if (config.useAllSlotsInDirectMode && Settings.canDrawOverlays(this)) {
                    startService(Intent(this, GearOverlayService::class.java))
                }
                AppMode.MIX -> startActivity(Intent(this, QuickPickActivity::class.java))
                AppMode.LIST -> Unit
            }
            finish()
            return true
        }

        val sharedText = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
        if (config.slots.any { it.isConfigured } && (isPlainTap || sharedText != null)) {
            startActivity(Intent(this, QuickPickActivity::class.java).putExtra(EXTRA_SHARED_TEXT, sharedText))
            finish()
            return true
        }
        return false
    }

    /**
     * MainActivity only has two real entry points — a launcher tap and an ACTION_SEND share —
     * plus internal shortcut/configure Intents already handled above. "Not a share" is a more
     * robust plain-tap signal than requiring an exact ACTION_MAIN/CATEGORY_LAUNCHER match, since
     * some OEM launchers don't deliver that combo exactly.
     */
    private fun isPlainLauncherTap(intent: Intent): Boolean =
        intent.action != Intent.ACTION_SEND

}

@androidx.compose.runtime.Composable
private fun NoAppRoot(
    mode: AppMode,
    slots: androidx.compose.runtime.snapshots.SnapshotStateList<ShortcutSlot>,
    useAllSlotsInDirectMode: Boolean,
    iconVariant: String,
    showPeekBubble: Boolean,
    showRecentApps: Boolean,
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
    onSlotsChanged: (List<ShortcutSlot>) -> Unit,
    onModeChanged: (AppMode) -> Unit,
    onUseAllSlotsInDirectModeChanged: (Boolean) -> Unit,
    onIconVariantChanged: (String) -> Unit,
    onShowPeekBubbleChanged: (Boolean) -> Unit,
    onShowRecentAppsChanged: (Boolean) -> Unit,
    onConfigImported: (AppConfig) -> Unit
) {
    if (screen !is Screen.Config) {
        BackHandler { onScreenChange(Screen.Config) }
    }

    when (screen) {
        is Screen.Config -> ConfigScreen(
            mode = mode,
            slots = slots,
            showPeekBubble = showPeekBubble,
            onEditSlot = { index -> onScreenChange(Screen.EditSlot(index)) },
            onAddSlot = { type -> onScreenChange(Screen.NewSlot(type)) },
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

        is Screen.NewSlot -> {
            // Fills the first empty gap (e.g. left by Fill or a swipe-delete) before
            // appending a new row, same as Fill's own fill-in-place-first behavior.
            val targetIndex = slots.indexOfFirst { !it.isConfigured }.let { if (it < 0) slots.size else it }
            SlotEditScreen(
                mode = mode,
                slot = ShortcutSlot(id = targetIndex, type = screen.type),
                onSave = { created ->
                    val next = if (targetIndex < slots.size) {
                        slots.toMutableList().also { it[targetIndex] = created }
                    } else {
                        slots + created
                    }
                    onSlotsChanged(next)
                    onScreenChange(Screen.Config)
                },
                onCancel = { onScreenChange(Screen.Config) }
            )
        }

        is Screen.Settings -> SettingsScreen(
            config = AppConfig(mode, slots.toList(), useAllSlotsInDirectMode, iconVariant, showPeekBubble, showRecentApps),
            onImportConfig = onConfigImported,
            onUseAllSlotsInDirectModeChanged = onUseAllSlotsInDirectModeChanged,
            onIconVariantChanged = onIconVariantChanged,
            onShowPeekBubbleChanged = onShowPeekBubbleChanged,
            onShowRecentAppsChanged = onShowRecentAppsChanged,
            onBack = { onScreenChange(Screen.Config) }
        )
    }
}
