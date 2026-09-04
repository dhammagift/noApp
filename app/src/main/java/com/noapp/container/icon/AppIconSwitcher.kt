package com.noapp.container.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.noapp.container.DebugLog
import com.noapp.container.R

/**
 * One selectable launcher icon. Two activity-alias entries in the manifest share its artwork:
 * [mainComponentSuffix] is the one this code enables; [listComponentSuffix] is a legacy alias
 * that older versions enabled in LIST mode (back when it targeted QuickPickActivity directly).
 * Both now target MainActivity, so an install that still has its "*List" alias enabled keeps
 * working unchanged — the manifest keeps declaring them precisely so that enabled state stays
 * valid across the update (a removed component's setting is dropped, and an install whose only
 * enabled alias was the removed one would be left with no launcher icon at all).
 */
data class IconVariant(val id: String, val mainComponentSuffix: String, val listComponentSuffix: String, val previewRes: Int)

// Component class names are rooted at the Kotlin/manifest namespace (com.noapp.container),
// which is independent of the app's applicationId (gift.dhamma.noapp) used for ComponentName's
// package argument at runtime — the two are not the same string, don't conflate them.
private const val NAMESPACE = "com.noapp.container"
private const val DEFAULT_VARIANT_ID = "default"

val ICON_VARIANTS = listOf(
    IconVariant(DEFAULT_VARIANT_ID, ".IconDefault", ".IconDefaultList", R.drawable.ic_launcher_foreground),
    IconVariant("material", ".IconMaterial", ".IconMaterialList", R.drawable.ic_launcher_foreground_material),
    IconVariant("bolt", ".IconBolt", ".IconBoltList", R.drawable.ic_launcher_foreground_bolt),
    IconVariant("boost", ".IconBoost", ".IconBoostList", R.drawable.ic_launcher_foreground_boost),
    IconVariant("electric", ".IconElectric", ".IconElectricList", R.drawable.ic_launcher_foreground_electric),
    IconVariant("flash", ".IconFlash", ".IconFlashList", R.drawable.ic_launcher_foreground_flash),
    IconVariant("sankha_flat", ".IconSankhaFlat", ".IconSankhaFlatList", R.drawable.ic_launcher_foreground_sankha_flat),
    IconVariant("sankha_3d", ".IconSankha3d", ".IconSankha3dList", R.drawable.ic_launcher_foreground_sankha_3d)
)

/** A variant removed from the list but still persisted from an older install falls back to the first one. */
fun resolveVariantId(variantId: String): String =
    if (ICON_VARIANTS.any { it.id == variantId }) variantId else ICON_VARIANTS.first().id

/**
 * Makes the launcher show exactly the icon for [variantId]: enables that variant's alias if none
 * of its two is on, and disables every other variant's aliases. The app's MODE is deliberately
 * not an input here anymore — every alias targets MainActivity, which reads the mode itself.
 *
 * WHY THIS MUST NEVER RUN WHILE THE APP IS OPEN, AND WHY [MainActivity] RESTARTS WHEN IT RETURNS
 * true: PackageManager batches component enabled-state changes made with DONT_KILL_APP and
 * broadcasts them about a second later; on receiving that, the system finishes any task whose
 * root intent names one of the changed components — which is exactly the alias a launcher tap
 * came in through. DONT_KILL_APP only spares the process, not the task, and the effect is the
 * same whether the alias is set to DISABLED or merely reset to DEFAULT (an earlier version bet
 * on DEFAULT being exempt and reintroduced "the app closes by itself ~1s after a mode change").
 * Confirmed on a real device via DebugLog: onPause/onStop(isFinishing)/onDestroy with no user
 * input, ~1s after the switch. So the only safe pattern is the one MainActivity uses: call this
 * before showing anything on a fresh launch, and if it changed anything, immediately relaunch
 * into a task whose root intent names MainActivity's own class (FLAG_ACTIVITY_CLEAR_TASK
 * re-roots the task) — by the time the batched broadcast lands, nothing references the old alias.
 *
 * Also why this is defensive (runCatching) like ShortcutSync: it's an OS call on the launch path,
 * and a PackageManager quirk on some OEM build must never take the launch down with it. Worst
 * case the icon lags one more launch behind.
 */
fun applyLauncherComponent(context: Context, variantId: String): Boolean {
    val result = runCatching {
        val pm = context.packageManager
        val chosenId = resolveVariantId(variantId)
        var changed = false
        for (variant in ICON_VARIANTS) {
            val main = variant.mainComponent(context)
            val list = variant.listComponent(context)
            val mainOn = isEnabled(pm, main, manifestDefault = variant.id == DEFAULT_VARIANT_ID)
            val listOn = isEnabled(pm, list, manifestDefault = false)
            if (variant.id == chosenId) {
                when {
                    !mainOn && !listOn -> { setEnabled(pm, main, true); changed = true }
                    // Both on can only be a half-applied earlier switch; converge on the main one.
                    mainOn && listOn -> { setEnabled(pm, list, false); changed = true }
                    // Exactly one on: leave it. Either alias is a valid icon for this variant, and
                    // flipping the legacy one over would only re-trigger the teardown for nothing.
                }
            } else {
                if (mainOn) { setEnabled(pm, main, false); changed = true }
                if (listOn) { setEnabled(pm, list, false); changed = true }
            }
        }
        changed
    }
    result.onFailure { DebugLog.log(context, "AppIconSwitcher", "applyLauncherComponent threw: $it") }
    return result.getOrDefault(false)
}

/**
 * The launcher alias that is enabled right now — needed so ShortcutManagerCompat shortcuts (see
 * ShortcutSync) can be tied to it explicitly via ShortcutInfoCompat.Builder.setActivity(...).
 * Without that, several launchers silently show no shortcuts at all for an app whose launcher
 * icon is an activity-alias rather than a plain activity. Read from PackageManager rather than
 * derived from the persisted icon variant: between an icon change and the next launch that
 * applies it, the OLD alias is still the enabled one, and shortcuts pinned to a not-yet-enabled
 * alias would vanish from the long-press menu until then.
 */
fun enabledLauncherComponent(context: Context): ComponentName {
    val fallback = ICON_VARIANTS.first().mainComponent(context)
    return runCatching {
        val pm = context.packageManager
        for (variant in ICON_VARIANTS) {
            val main = variant.mainComponent(context)
            if (isEnabled(pm, main, manifestDefault = variant.id == DEFAULT_VARIANT_ID)) return main
            val list = variant.listComponent(context)
            if (isEnabled(pm, list, manifestDefault = false)) return list
        }
        fallback
    }.getOrDefault(fallback)
}

private fun IconVariant.mainComponent(context: Context) = ComponentName(context.packageName, "$NAMESPACE$mainComponentSuffix")
private fun IconVariant.listComponent(context: Context) = ComponentName(context.packageName, "$NAMESPACE$listComponentSuffix")

// Only ".IconDefault" ships android:enabled="true" in the manifest; every other alias defaults
// to false — that's what resolves COMPONENT_ENABLED_STATE_DEFAULT (never explicitly set yet).
private fun isEnabled(pm: PackageManager, component: ComponentName, manifestDefault: Boolean): Boolean =
    when (pm.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
        else -> false
    }

private fun setEnabled(pm: PackageManager, component: ComponentName, enabled: Boolean) {
    pm.setComponentEnabledSetting(
        component,
        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
}
