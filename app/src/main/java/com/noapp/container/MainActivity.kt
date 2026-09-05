package com.noapp.container

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.noapp.container.data.ConfigStore
import com.noapp.container.icon.applyLauncherComponent
import com.noapp.container.model.AppConfig
import com.noapp.container.model.AppMode
import com.noapp.container.model.AppTheme
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType
import com.noapp.container.recents.RecentApps
import com.noapp.container.shortcuts.ActionDispatcher
import com.noapp.container.shortcuts.EXTRA_OPEN_CONFIG
import com.noapp.container.shortcuts.EXTRA_SLOT_ID
import com.noapp.container.shortcuts.GearOverlayService
import com.noapp.container.shortcuts.ShortcutSync
import com.noapp.container.ui.ConfigScreen
import com.noapp.container.ui.CrashReportScreen
import com.noapp.container.ui.SettingsScreen
import com.noapp.container.ui.SlotEditScreen
import com.noapp.container.ui.UiHint
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
    private var hintSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log(this, TAG, "onCreate hash=${System.identityHashCode(this)} action=${intent.action} extras=${intent.extras?.keySet()}")

        val initialConfig = ConfigStore.load(this)
        if (CrashLogger.consumePendingCrash(this)) {
            // Show the log instead of the normal screen on the very next launch after a real
            // crash, so it can be copied/shared straight off the phone — see CrashLogger.
            // recreate() runs the rest of onCreate fresh once dismissed, same as any cold start.
            setContent {
                NoAppTheme(initialConfig.theme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        CrashReportScreen(DebugLog.read(this), onDismiss = { recreate() })
                    }
                }
            }
            return
        }

        if (reconcileLauncherIconOrRestart(intent, initialConfig)) return
        if (dispatchIfShortcut(intent, initialConfig)) {
            DebugLog.log(this, TAG, "dispatchIfShortcut handled it, finishing")
            return
        }

        // Self-heals installs whose shortcuts were published before ShortcutSync started
        // pinning them to the enabled alias explicitly (see its own doc comment) — those
        // never show up in the long-press menu at all until re-synced, and nothing else in
        // this app calls sync() except an actual edit. Runs on ShortcutSync's own thread, so
        // it costs the launch nothing.
        ShortcutSync.sync(this, initialConfig.mode, initialConfig.slots, initialConfig.useAllSlotsInDirectMode)
        DebugLog.log(this, TAG, "showing Config screen")

        setContent {
            var mode by remember { mutableStateOf(initialConfig.mode) }
            val slots = remember { mutableStateListOf(*initialConfig.slots.toTypedArray()) }
            var useAllSlotsInDirectMode by remember { mutableStateOf(initialConfig.useAllSlotsInDirectMode) }
            var iconVariant by remember { mutableStateOf(initialConfig.iconVariant) }
            var showPeekBubble by remember { mutableStateOf(initialConfig.showPeekBubble) }
            var peekBubbleReturns by remember { mutableStateOf(initialConfig.peekBubbleReturns) }
            var peekBubbleSize by remember { mutableStateOf(initialConfig.peekBubbleSize) }
            var peekBubbleAlpha by remember { mutableStateOf(initialConfig.peekBubbleAlpha) }
            var showRecentApps by remember { mutableStateOf(initialConfig.showRecentApps) }
            var theme by remember { mutableStateOf(initialConfig.theme) }
            // The one pending Snackbar message, if any — shown by whichever screen is up on its
            // own Scaffold's SnackbarHost (already positioned above its FAB and the system bars),
            // and cleared through onHintShown the moment that screen picks it up. See UiHint.
            var hint by remember { mutableStateOf<UiHint?>(null) }

            fun showHint(text: String) {
                hint = UiHint(++hintSeq, text)
            }

            // Everything here takes effect immediately and completely: the mode, the toggles and
            // the slots are all just config that the next launcher tap reads back. The one thing
            // deliberately NOT done here is touching the launcher alias for an icon change — see
            // reconcileLauncherIconOrRestart for why that can only ever happen on a fresh launch.
            fun persist() {
                val config = AppConfig(mode, slots.toList(), useAllSlotsInDirectMode, iconVariant, showPeekBubble, peekBubbleReturns, peekBubbleSize, peekBubbleAlpha, showRecentApps, theme)
                ConfigStore.save(this, config)
                ShortcutSync.sync(this, mode, slots.toList(), useAllSlotsInDirectMode)
                DebugLog.log(this, TAG, "persist: done mode=$mode variant=$iconVariant")
            }

            NoAppTheme(theme) {
                // The window itself is translucent (see Theme.NoApp.Main in themes.xml) — this is
                // what makes these screens opaque.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NoAppRoot(
                        mode = mode,
                        slots = slots,
                        useAllSlotsInDirectMode = useAllSlotsInDirectMode,
                        iconVariant = iconVariant,
                        showPeekBubble = showPeekBubble,
                        peekBubbleReturns = peekBubbleReturns,
                        peekBubbleSize = peekBubbleSize,
                        peekBubbleAlpha = peekBubbleAlpha,
                        showRecentApps = showRecentApps,
                        theme = theme,
                        screen = screen,
                        hint = hint,
                        onHintShown = { shown -> if (hint?.id == shown.id) hint = null },
                        onScreenChange = { screen = it },
                        onSlotsChanged = { updated ->
                            slots.clear()
                            slots.addAll(updated)
                            persist()
                        },
                        onModeChanged = { newMode ->
                            mode = newMode
                            persist()
                            showHint(modeHintText(newMode))
                        },
                        onUseAllSlotsInDirectModeChanged = { value ->
                            useAllSlotsInDirectMode = value
                            persist()
                        },
                        onIconVariantChanged = { value ->
                            iconVariant = value
                            persist()
                            showHint(getString(R.string.icon_hint_message))
                        },
                        onShowPeekBubbleChanged = { value ->
                            showPeekBubble = value
                            persist()
                        },
                        onPeekBubbleReturnsChanged = { value ->
                            peekBubbleReturns = value
                            persist()
                        },
                        onPeekBubbleSizeChanged = { value ->
                            peekBubbleSize = value
                            persist()
                        },
                        onPeekBubbleAlphaChanged = { value ->
                            peekBubbleAlpha = value
                            persist()
                        },
                        onShowRecentAppsChanged = { value ->
                            showRecentApps = value
                            persist()
                        },
                        onThemeChanged = { value ->
                            theme = value
                            persist()
                        },
                        onConfigImported = { imported ->
                            val iconChanged = imported.iconVariant != iconVariant
                            mode = imported.mode
                            slots.clear()
                            slots.addAll(imported.slots)
                            // A backed-up config claiming one of these permission-gated features was
                            // on doesn't mean the permission is actually granted on THIS device/install
                            // — the normal toggle flow always checks before flipping to on, and
                            // importing shouldn't be a way around that (a switch showing "on" with no
                            // real permission behind it is exactly the confusing state that flow
                            // prevents).
                            useAllSlotsInDirectMode = imported.useAllSlotsInDirectMode && Settings.canDrawOverlays(this)
                            iconVariant = imported.iconVariant
                            showPeekBubble = imported.showPeekBubble && Settings.canDrawOverlays(this)
                            peekBubbleReturns = imported.peekBubbleReturns
                            peekBubbleSize = imported.peekBubbleSize
                            peekBubbleAlpha = imported.peekBubbleAlpha
                            showRecentApps = imported.showRecentApps && RecentApps.hasUsageAccess(this)
                            theme = imported.theme
                            persist()
                            if (iconChanged) showHint(getString(R.string.icon_hint_message))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugLog.log(this, TAG, "onNewIntent hash=${System.identityHashCode(this)} action=${intent.action} extras=${intent.extras?.keySet()}")
        val config = ConfigStore.load(this)
        // Same as on a fresh launch: an icon changed in this very instance (Settings is still
        // open, user went Home, tapped the launcher icon again) gets applied now rather than
        // one launch later — with the same clean-task restart, since this task's root intent
        // still names the alias about to be switched off.
        if (reconcileLauncherIconOrRestart(intent, config)) return
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) {
            // QuickPickActivity redirects here with this extra when there's nothing configured
            // yet to show — e.g. this instance was already running in the background on some
            // other screen. Navigate to Config so the user actually lands somewhere they can
            // add a shortcut, instead of just resurfacing whatever screen was left open.
            screen = Screen.Config
        } else {
            dispatchIfShortcut(intent, config)
            // A repeat share/tap while the UI is already open is rare enough to just leave the
            // current screen as-is rather than re-plumb intent state into the composition.
        }
    }

    /**
     * Makes the launcher icon match [config]'s icon variant. This is the ONLY place the launcher
     * aliases are ever touched, and it's done before anything is shown — because Android tears
     * down any task whose root intent names an alias whose enabled state just changed, about a
     * second after the change and regardless of DONT_KILL_APP (see applyLauncherComponent's doc
     * comment; confirmed on-device). A launcher tap always arrives through the currently enabled
     * alias, so if this changed anything, that alias IS this task's root: immediately relaunch
     * into a task rooted at MainActivity's own class instead, and finish this one. FLAG_ACTIVITY_
     * CLEAR_TASK re-roots the task, so by the time the batched broadcast lands, nothing left
     * references the old alias. Keeps the original intent's action/extras so a shortcut/share
     * tap still gets dispatched correctly on the next pass — but forces the component to
     * MainActivity's own class rather than copying intent's as-is: when launched through an
     * activity-alias, Intent.getComponent() names that ALIAS, and an explicit Intent to a just-
     * disabled component throws ActivityNotFoundException. Nearly always a no-op: it only ever
     * changes anything on the first launch after Settings > App icon was changed.
     *
     * Returns true if it restarted (caller must return without doing anything else).
     */
    private fun reconcileLauncherIconOrRestart(intent: Intent, config: AppConfig): Boolean {
        val changed = applyLauncherComponent(this, config.iconVariant)
        DebugLog.log(this, TAG, "reconcile launcher icon variant=${config.iconVariant} changed=$changed")
        if (!changed) return false
        // The long-press shortcuts are pinned to the enabled alias (see ShortcutSync) — re-pin
        // them to the new one now, even if the relaunch below goes straight to a dispatch and
        // never reaches the Config screen's own sync.
        ShortcutSync.sync(this, config.mode, config.slots, config.useAllSlotsInDirectMode)
        DebugLog.log(this, TAG, "launcher alias changed, restarting into a clean task")
        startActivity(
            Intent(intent)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
        return true
    }

    private fun modeHintText(mode: AppMode): String = getString(
        when (mode) {
            AppMode.LIST -> R.string.restart_hint_message_list
            AppMode.DIRECT -> R.string.restart_hint_message_direct
            AppMode.MIX -> R.string.restart_hint_message_mix
        }
    )

    /**
     * Returns true (and finishes the activity) if [intent] should be handled without ever
     * showing MainActivity's own UI: an explicit shortcut tap or a plain DIRECT/MIX-mode tap
     * dispatch straight to slot 0 (always instant — a tappable Configure gear also flashes on
     * top via [GearOverlayService], DIRECT only); MIX additionally opens the same
     * [QuickPickActivity] sheet LIST uses, on top of whatever slot 0 just launched; a plain
     * LIST-mode tap, a MIX tap with slot 0 unconfigured, or an incoming share also opens that
     * translucent sheet so it overlays whatever was on screen. This Activity is translucent
     * and has no starting window (see themes.xml), so none of these ever flash anything of
     * their own.
     */
    private fun dispatchIfShortcut(intent: Intent, config: AppConfig): Boolean {
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) return false // Configure entry: show UI instead

        val explicitId = intent.getIntExtra(EXTRA_SLOT_ID, -1)
        if (explicitId >= 0) {
            config.slots.getOrNull(explicitId)?.let { ActionDispatcher.execute(this, it) }
            finishWithoutTransition()
            return true
        }

        val isPlainTap = isPlainLauncherTap(intent)
        val isPlainMainTap = (config.mode == AppMode.DIRECT || config.mode == AppMode.MIX) &&
            isPlainTap &&
            config.slots.getOrNull(0)?.isConfigured == true
        if (isPlainMainTap) {
            config.slots.getOrNull(0)?.let { ActionDispatcher.execute(this, it) }
            when (config.mode) {
                // Always shown for Direct, not just when useAllSlotsInDirectMode frees up the
                // long-press Configure entry — having it appear in some cases but not others was
                // confusing. Whether Configure also has its own long-press entry is still purely
                // the toggle's call (see ShortcutSync); this is just an always-available second path.
                AppMode.DIRECT -> if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, GearOverlayService::class.java))
                }
                AppMode.MIX -> startActivity(Intent(this, QuickPickActivity::class.java))
                AppMode.LIST -> Unit
            }
            finishWithoutTransition()
            return true
        }

        val sharedText = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
        if (config.slots.any { it.isConfigured } && (isPlainTap || sharedText != null)) {
            startActivity(Intent(this, QuickPickActivity::class.java).putExtra(EXTRA_SHARED_TEXT, sharedText))
            finishWithoutTransition()
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

    /** Every dispatchIfShortcut finish() follows this: nothing of ours was ever meant to be seen. */
    @Suppress("DEPRECATION")
    private fun finishWithoutTransition() {
        finish()
        overridePendingTransition(0, 0)
    }

    // These three, logged with the activity's identity hash, are what actually shows whether the
    // system is tearing this instance down on its own (no matching user-initiated onNewIntent/
    // back-press before them) — the smoking gun for "closes itself, no crash" if that's really an
    // OS-level task teardown rather than a JVM exception.
    override fun onPause() {
        super.onPause()
        DebugLog.log(this, TAG, "onPause hash=${System.identityHashCode(this)}")
    }

    override fun onStop() {
        super.onStop()
        DebugLog.log(this, TAG, "onStop hash=${System.identityHashCode(this)} isFinishing=$isFinishing")
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLog.log(this, TAG, "onDestroy hash=${System.identityHashCode(this)} isFinishing=$isFinishing")
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

@androidx.compose.runtime.Composable
private fun NoAppRoot(
    mode: AppMode,
    slots: androidx.compose.runtime.snapshots.SnapshotStateList<ShortcutSlot>,
    useAllSlotsInDirectMode: Boolean,
    iconVariant: String,
    showPeekBubble: Boolean,
    peekBubbleReturns: Boolean,
    peekBubbleSize: Float,
    peekBubbleAlpha: Float,
    showRecentApps: Boolean,
    theme: AppTheme,
    screen: Screen,
    hint: UiHint?,
    onHintShown: (UiHint) -> Unit,
    onScreenChange: (Screen) -> Unit,
    onSlotsChanged: (List<ShortcutSlot>) -> Unit,
    onModeChanged: (AppMode) -> Unit,
    onUseAllSlotsInDirectModeChanged: (Boolean) -> Unit,
    onIconVariantChanged: (String) -> Unit,
    onShowPeekBubbleChanged: (Boolean) -> Unit,
    onPeekBubbleReturnsChanged: (Boolean) -> Unit,
    onPeekBubbleSizeChanged: (Float) -> Unit,
    onPeekBubbleAlphaChanged: (Float) -> Unit,
    onShowRecentAppsChanged: (Boolean) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
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
            hint = hint,
            onHintShown = onHintShown,
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
            config = AppConfig(mode, slots.toList(), useAllSlotsInDirectMode, iconVariant, showPeekBubble, peekBubbleReturns, peekBubbleSize, peekBubbleAlpha, showRecentApps, theme),
            hint = hint,
            onHintShown = onHintShown,
            onImportConfig = onConfigImported,
            onUseAllSlotsInDirectModeChanged = onUseAllSlotsInDirectModeChanged,
            onIconVariantChanged = onIconVariantChanged,
            onShowPeekBubbleChanged = onShowPeekBubbleChanged,
            onPeekBubbleReturnsChanged = onPeekBubbleReturnsChanged,
            onPeekBubbleSizeChanged = onPeekBubbleSizeChanged,
            onPeekBubbleAlphaChanged = onPeekBubbleAlphaChanged,
            onShowRecentAppsChanged = onShowRecentAppsChanged,
            onThemeChanged = onThemeChanged,
            onBack = { onScreenChange(Screen.Config) }
        )
    }
}
