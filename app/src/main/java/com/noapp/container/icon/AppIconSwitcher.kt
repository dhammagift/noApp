package com.noapp.container.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.noapp.container.DebugLog
import com.noapp.container.R
import com.noapp.container.model.AppMode

/**
 * One selectable launcher icon. It has two activity-alias entries in the manifest sharing this
 * same icon artwork: [mainComponentSuffix] (targets MainActivity, used outside LIST mode) and
 * [listComponentSuffix] (targets QuickPickActivity, used in LIST mode — see [applyLauncherComponent]).
 */
data class IconVariant(val id: String, val mainComponentSuffix: String, val listComponentSuffix: String, val previewRes: Int)

// Component class names are rooted at the Kotlin/manifest namespace (com.noapp.container),
// which is independent of the app's applicationId (gift.dhamma.noapp) used for ComponentName's
// package argument at runtime — the two are not the same string, don't conflate them.
private const val NAMESPACE = "com.noapp.container"

val ICON_VARIANTS = listOf(
    IconVariant("default", ".IconDefault", ".IconDefaultList", R.drawable.ic_launcher_foreground),
    IconVariant("material", ".IconMaterial", ".IconMaterialList", R.drawable.ic_launcher_foreground_material),
    IconVariant("bolt", ".IconBolt", ".IconBoltList", R.drawable.ic_launcher_foreground_bolt),
    IconVariant("boost", ".IconBoost", ".IconBoostList", R.drawable.ic_launcher_foreground_boost),
    IconVariant("electric", ".IconElectric", ".IconElectricList", R.drawable.ic_launcher_foreground_electric),
    IconVariant("flash", ".IconFlash", ".IconFlashList", R.drawable.ic_launcher_foreground_flash),
    IconVariant("sankha_flat", ".IconSankhaFlat", ".IconSankhaFlatList", R.drawable.ic_launcher_foreground_sankha_flat),
    IconVariant("sankha_3d", ".IconSankha3d", ".IconSankha3dList", R.drawable.ic_launcher_foreground_sankha_3d)
)

/**
 * Exactly one of the 18 aliases is ENABLED at a time, the rest DISABLED: the chosen variant's
 * [IconVariant.listComponentSuffix] alias in [AppMode.LIST] (its target, QuickPickActivity, has
 * an already-invisible starting window — the only way to make a LIST-mode launcher tap never
 * flash a splash, since MainActivity's own theme is opaque and routing through it first, even
 * just to dispatch-and-finish, flashes that first), otherwise its [IconVariant.mainComponentSuffix]
 * alias (DIRECT/MIX: the normal OS starting window, showing the real icon, is expected here).
 * MainActivity's own component is never touched here — it stays enabled always, since
 * GearOverlayService, QuickPickActivity's Configure button, and ShortcutSync all target it by
 * explicit component, and an explicit Intent to a disabled component fails to launch (this broke
 * Settings access entirely in an earlier version). DONT_KILL_APP so switching doesn't restart the
 * process mid-Settings-screen. Some launchers take a moment (or a home-screen return) to pick up
 * the new icon — that's the OS, not us.
 *
 * Called ONLY from MainActivity.onCreate, on every cold start (before dispatch) — deliberately
 * not from persist() anymore. A mode/variant change takes effect in the app's own behavior
 * immediately either way; this reconciles which alias the OS actually sees as the launcher icon
 * for the next cold start, never live while the app is already open (see persist()'s own comment
 * for why: this is the one unguarded-looking OS call on the launch path regardless, so it's
 * defensive the same way ShortcutSync.sync is — a PackageManager quirk here (some OEM
 * launcher/PM combination this hasn't been tested against) must never take the whole launch down
 * with it. Worst case the launcher icon doesn't switch variant/mode target until the next
 * attempt — better than the app never opening at all.
 *
 * Deliberately synchronous and atomic (enable the target, disable everything else, in one pass)
 * rather than deferring the disable step: a previous attempt at delaying it left a stray extra
 * launcher icon behind whenever the app closed again before the delayed step ran — worse than
 * the single enabled/disabled pass this reverted to.
 *
 * Returns whether any component's enabled state actually changed. Confirmed on-device (see
 * DebugLog): whenever this disables a component whose alias the CURRENT task's history includes
 * — even if this exact Activity instance was reached via an explicit Intent, not that alias
 * directly — Android tears down the whole task shortly after, DONT_KILL_APP notwithstanding
 * (that flag only protects the process, not a task tied to a component that just went away). A
 * true no-op call (nothing needed to change) never triggers this. Callers use the return value
 * to proactively restart into a clean task instead of waiting to be silently killed.
 */
fun applyLauncherComponent(context: Context, variantId: String, mode: AppMode): Boolean {
    val result = runCatching {
        val pm = context.packageManager
        val appPackage = context.packageName
        val useListTarget = mode == AppMode.LIST
        // A variant removed from the list (e.g. after an update) but still persisted from an
        // older install would otherwise match nothing below, leaving every alias disabled — no
        // launcher icon at all. Fall back to the first variant instead.
        val resolvedVariantId = if (ICON_VARIANTS.any { it.id == variantId }) variantId else ICON_VARIANTS.first().id
        var changed = false
        for (variant in ICON_VARIANTS) {
            val isChosen = variant.id == resolvedVariantId
            val mainComponent = ComponentName(appPackage, "$NAMESPACE${variant.mainComponentSuffix}")
            val listComponent = ComponentName(appPackage, "$NAMESPACE${variant.listComponentSuffix}")
            // Only the "default" variant's main alias ships android:enabled="true" — every other
            // alias defaults to false — needed to resolve COMPONENT_ENABLED_STATE_DEFAULT (never
            // explicitly touched yet, i.e. this exact device/app-data combo's true first call).
            if (setIfChanged(pm, mainComponent, want = isChosen && !useListTarget, manifestDefault = variant.id == "default")) changed = true
            if (setIfChanged(pm, listComponent, want = isChosen && useListTarget, manifestDefault = false)) changed = true
        }
        changed
    }
    // Silent failure here would look identical to "nothing went wrong" in every other log line —
    // this is the one place worth logging even on success, since a caught-but-unlogged exception
    // was indistinguishable from no exception at all in earlier builds.
    result.onFailure { DebugLog.log(context, "AppIconSwitcher", "applyLauncherComponent threw: $it") }
    return result.getOrDefault(false)
}

private fun setIfChanged(pm: PackageManager, component: ComponentName, want: Boolean, manifestDefault: Boolean): Boolean {
    val currentlyEnabled = when (pm.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
        else -> false
    }
    if (currentlyEnabled == want) return false
    pm.setComponentEnabledSetting(component, stateFor(want), PackageManager.DONT_KILL_APP)
    return true
}

private fun stateFor(enabled: Boolean) =
    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

/**
 * The one alias [applyLauncherComponent] leaves enabled for [variantId]/[mode] — needed so
 * ShortcutManagerCompat shortcuts (see ShortcutSync) can be tied to it explicitly via
 * ShortcutInfoCompat.Builder.setActivity(...). Without that, several launchers silently show
 * no shortcuts at all for an app whose actual launcher icon is an activity-alias rather than
 * a plain activity — there's no single unambiguous "default activity" for them to fall back to
 * across 16 aliases, only one of which is ever enabled.
 */
fun enabledLauncherComponent(context: Context, variantId: String, mode: AppMode): ComponentName {
    val resolvedVariantId = if (ICON_VARIANTS.any { it.id == variantId }) variantId else ICON_VARIANTS.first().id
    val variant = ICON_VARIANTS.first { it.id == resolvedVariantId }
    val suffix = if (mode == AppMode.LIST) variant.listComponentSuffix else variant.mainComponentSuffix
    return ComponentName(context.packageName, "$NAMESPACE$suffix")
}
