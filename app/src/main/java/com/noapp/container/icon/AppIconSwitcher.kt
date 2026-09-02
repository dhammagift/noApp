package com.noapp.container.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.noapp.container.MainActivity
import com.noapp.container.R

/** One selectable launcher icon. [componentSuffix] is null for the default (MainActivity itself). */
data class IconVariant(val id: String, val componentSuffix: String?, val previewRes: Int)

// Component class names are rooted at the Kotlin/manifest namespace (com.noapp.container),
// which is independent of the app's applicationId (gift.dhamma.noapp) used for ComponentName's
// package argument at runtime — the two are not the same string, don't conflate them.
private const val NAMESPACE = "com.noapp.container"

val ICON_VARIANTS = listOf(
    IconVariant("default", null, R.drawable.ic_launcher_foreground),
    IconVariant("bolt", ".IconBolt", R.drawable.ic_launcher_foreground_bolt),
    IconVariant("boost", ".IconBoost", R.drawable.ic_launcher_foreground_boost),
    IconVariant("electric", ".IconElectric", R.drawable.ic_launcher_foreground_electric),
    IconVariant("flash", ".IconFlash", R.drawable.ic_launcher_foreground_flash),
    IconVariant("sankha_flat", ".IconSankhaFlat", R.drawable.ic_launcher_foreground_sankha_flat),
    IconVariant("sankha_3d", ".IconSankha3d", R.drawable.ic_launcher_foreground_sankha_3d),
    IconVariant("sankha_bckgr", ".IconSankhaBckgr", R.drawable.ic_launcher_foreground_sankha_bckgr)
)

/**
 * Exactly one of MainActivity's own launcher component and the icon-variant aliases is
 * ENABLED at a time; the rest are DISABLED. DONT_KILL_APP so switching doesn't restart the
 * process mid-Settings-screen. Some launchers take a moment (or a home-screen return) to
 * pick up the new icon — that's the OS, not us.
 */
fun applyIconVariant(context: Context, variantId: String) {
    val pm = context.packageManager
    val target = ICON_VARIANTS.firstOrNull { it.id == variantId } ?: ICON_VARIANTS.first()
    val appPackage = context.packageName

    pm.setComponentEnabledSetting(
        ComponentName(context, MainActivity::class.java),
        if (target.componentSuffix == null) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
    for (variant in ICON_VARIANTS) {
        val suffix = variant.componentSuffix ?: continue
        pm.setComponentEnabledSetting(
            ComponentName(appPackage, "$NAMESPACE$suffix"),
            if (variant.id == variantId) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
