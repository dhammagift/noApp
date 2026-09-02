package com.noapp.container.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.noapp.container.R

/** One selectable launcher icon; [componentSuffix] names its activity-alias in the manifest. */
data class IconVariant(val id: String, val componentSuffix: String, val previewRes: Int)

// Component class names are rooted at the Kotlin/manifest namespace (com.noapp.container),
// which is independent of the app's applicationId (gift.dhamma.noapp) used for ComponentName's
// package argument at runtime — the two are not the same string, don't conflate them.
private const val NAMESPACE = "com.noapp.container"

val ICON_VARIANTS = listOf(
    IconVariant("default", ".IconDefault", R.drawable.ic_launcher_foreground),
    IconVariant("material", ".IconMaterial", R.drawable.ic_launcher_foreground_material),
    IconVariant("bolt", ".IconBolt", R.drawable.ic_launcher_foreground_bolt),
    IconVariant("boost", ".IconBoost", R.drawable.ic_launcher_foreground_boost),
    IconVariant("electric", ".IconElectric", R.drawable.ic_launcher_foreground_electric),
    IconVariant("flash", ".IconFlash", R.drawable.ic_launcher_foreground_flash),
    IconVariant("sankha_flat", ".IconSankhaFlat", R.drawable.ic_launcher_foreground_sankha_flat),
    IconVariant("sankha_3d", ".IconSankha3d", R.drawable.ic_launcher_foreground_sankha_3d),
    IconVariant("sankha_bckgr", ".IconSankhaBckgr", R.drawable.ic_launcher_foreground_sankha_bckgr)
)

/**
 * Exactly one alias is ENABLED at a time, the rest DISABLED. MainActivity's own component is
 * never touched here — it stays enabled always, since GearOverlayService, QuickPickActivity
 * and ShortcutSync all target it by explicit component, and an explicit Intent to a disabled
 * component fails to launch (this broke Settings access entirely in an earlier version).
 * DONT_KILL_APP so switching doesn't restart the process mid-Settings-screen. Some launchers
 * take a moment (or a home-screen return) to pick up the new icon — that's the OS, not us.
 */
fun applyIconVariant(context: Context, variantId: String) {
    val pm = context.packageManager
    val appPackage = context.packageName
    for (variant in ICON_VARIANTS) {
        pm.setComponentEnabledSetting(
            ComponentName(appPackage, "$NAMESPACE${variant.componentSuffix}"),
            if (variant.id == variantId) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
