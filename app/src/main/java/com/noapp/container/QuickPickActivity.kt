package com.noapp.container

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noapp.container.data.ConfigStore
import com.noapp.container.model.AppMode
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.shortcuts.EXTRA_OPEN_CONFIG
import com.noapp.container.shortcuts.QuickPickPeekOverlayService
import com.noapp.container.ui.QuickPickSheet
import com.noapp.container.ui.theme.NoAppTheme

const val EXTRA_SHARED_TEXT = "extra_shared_text"

/**
 * A separate, translucent Activity (see Theme.NoApp.Transparent) just for the
 * plain-tap LIST-mode picker and the share-target sheet — so it renders as a
 * popup over whatever was already on screen instead of a full opaque app switch.
 * MainActivity keeps everything else (Config/Settings/edit screens).
 *
 * Also the direct launcher entry point in LIST mode (see the "*List" aliases in the
 * manifest and icon/AppIconSwitcher.kt): its own starting window is already invisible,
 * which a launcher tap routed through MainActivity's opaque theme first never could be.
 * That means a fresh install — LIST mode by default, nothing configured yet — can land
 * here directly with zero slots; redirect straight to Configure instead of showing an
 * empty sheet.
 *
 * singleTask (see the manifest entry): an impatient repeat tap or share reuses this one
 * instance via [onNewIntent] instead of stacking a second translucent sheet on top of the
 * first, which used to leave a duplicate list visible underneath once the top one was
 * dismissed. [slots]/[sharedText]/[allowPeek]/[showRecentApps] are held as Compose state
 * (not plain onCreate-time vals) so a re-triggered [onNewIntent] can refresh them.
 */
class QuickPickActivity : ComponentActivity() {
    // Set in loadAndDispatch; onStop reuses it so leaving the app by backgrounding it
    // (Home, recents, switching apps) leaves the same peek bubble behind that swiping
    // the list away does, instead of the sheet just silently vanishing either way
    // depending on which one you happened to use.
    private var allowPeek by mutableStateOf(false)
    private var slots by mutableStateOf<List<ShortcutSlot>>(emptyList())
    private var sharedText by mutableStateOf<String?>(null)
    private var showRecentApps by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!loadAndDispatch(intent)) return

        setContent {
            NoAppTheme {
                // Keyed on the content itself so a singleTask re-trigger (onNewIntent calling
                // loadAndDispatch again) rebuilds the sheet's own remembered animation state
                // instead of reusing a composition that may already be mid-dismiss.
                key(slots, sharedText) {
                    QuickPickSheet(
                        slots = slots,
                        sharedText = sharedText,
                        allowPeek = allowPeek,
                        // Recent apps are a plain launch, not a share target, so this stays off
                        // for the share sheet the same way allowPeek does.
                        showRecentApps = showRecentApps,
                        onConfigure = {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .putExtra(EXTRA_OPEN_CONFIG, true)
                            )
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAndDispatch(intent)
    }

    /** Returns false if it already redirected to Configure and finished this activity. */
    private fun loadAndDispatch(intent: Intent): Boolean {
        // A fresh dispatch always supersedes any peek bubble left over from a previous
        // one (harmless no-op if the service isn't running).
        stopService(Intent(this, QuickPickPeekOverlayService::class.java))
        val newSharedText = intent.getStringExtra(EXTRA_SHARED_TEXT)
        val config = ConfigStore.load(this)
        val newSlots = config.slots.filter { it.isConfigured }

        if (newSlots.isEmpty() && newSharedText == null) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_CONFIG, true))
            finish()
            return false
        }

        sharedText = newSharedText
        slots = newSlots
        // LIST and MIX both get the collapse-to-peek affordance, unless the user turned it
        // off in Settings; the share-target sheet keeps the plain dismiss-and-finish
        // regardless (there's no "list" to return to).
        allowPeek = (config.mode == AppMode.LIST || config.mode == AppMode.MIX) &&
            newSharedText == null &&
            config.showPeekBubble
        showRecentApps = (config.mode == AppMode.LIST || config.mode == AppMode.MIX) &&
            newSharedText == null &&
            config.showRecentApps
        return true
    }

    override fun onStop() {
        super.onStop()
        // isFinishing is already true here if the sheet's own onDismiss/onConfigure/item-tap
        // already handled this (including the swipe-away peek path, which starts the same
        // service itself) — this only fires for actually leaving via Home/recents/switching
        // apps while the sheet was still up. isChangingConfigurations excludes a plain
        // rotation, which also stops this Activity but isn't "leaving" it.
        if (!isFinishing && !isChangingConfigurations && allowPeek && Settings.canDrawOverlays(this)) {
            startService(Intent(this, QuickPickPeekOverlayService::class.java))
            finish()
        }
    }
}
