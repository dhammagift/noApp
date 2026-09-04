package com.noapp.container.icon

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * No hand-authored icon set: a user-set [ShortcutSlot.customIcon] (emoji or short
 * text) wins if present; otherwise App-type slots show the target app's own
 * launcher icon (packageManager already has it); everything else falls back to
 * an auto-generated colored monogram of the label. No icon-picker asset palette.
 */
fun iconBitmapFor(context: Context, slot: ShortcutSlot, sizePx: Int): Bitmap {
    if (slot.customIcon.isNotBlank()) {
        return monogramBitmap(slot.customIcon, slot.color.ifBlank { ShortcutSlot.DEFAULT_COLOR }, sizePx)
    }
    if (slot.type == SlotType.APP && slot.param.isNotBlank()) {
        appIconBitmapOrNull(context, slot.param, sizePx)?.let { return it }
    }
    return monogramBitmap(
        text = slot.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        colorHex = slot.color.ifBlank { ShortcutSlot.DEFAULT_COLOR },
        sizePx = sizePx
    )
}

/** Real app icon by package name, for the app picker rows — falls back to a "?" monogram. */
fun appIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap =
    appIconBitmapOrNull(context, packageName, sizePx)
        ?: monogramBitmap("?", ShortcutSlot.DEFAULT_COLOR, sizePx)

private fun appIconBitmapOrNull(context: Context, packageName: String, sizePx: Int): Bitmap? =
    runCatching { context.packageManager.getApplicationIcon(packageName) }
        .getOrNull()
        ?.toBitmap(sizePx, sizePx)

/** Public so ShortcutSync can reuse it for the non-slot "Configure" shortcut icon. */
fun monogramBitmap(text: String, colorHex: String, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex) }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bg)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    val fm = paint.fontMetrics
    canvas.drawText(text, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2f, paint)
    return bmp
}

/**
 * Monograms are drawn inline (cheap, and they'd only flicker if deferred); a real app icon
 * goes through PackageManager, which loads and decodes another package's resources — tens of
 * ms each on a slow device, and several rows of those used to sit on the first frame of the
 * Config list and, worse, of the LIST-mode sheet. Loaded off the main thread instead, with the
 * slot's space held open meanwhile so rows don't reflow when the icon lands.
 */
@Composable
fun SlotIcon(slot: ShortcutSlot, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    if (!slot.isConfigured) {
        Text("?", modifier = modifier.size(size))
        return
    }
    val isAppIcon = slot.customIcon.isBlank() && slot.type == SlotType.APP && slot.param.isNotBlank()
    if (!isAppIcon) {
        val bitmap = remember(slot.type, slot.label, slot.color, slot.customIcon, sizePx) {
            iconBitmapFor(context, slot, sizePx).asImageBitmap()
        }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
        return
    }
    val bitmap by produceState<ImageBitmap?>(null, slot.param, slot.label, slot.color, sizePx) {
        value = withContext(Dispatchers.IO) { iconBitmapFor(context, slot, sizePx).asImageBitmap() }
    }
    BitmapOrPlaceholder(bitmap, modifier.size(size))
}

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) { appIconBitmap(context, packageName, sizePx).asImageBitmap() }
    }
    BitmapOrPlaceholder(bitmap, modifier.size(size))
}

/**
 * A drawable resource decoded at roughly the size it's shown at, off the main thread. The icon
 * previews in Settings are 1024×1024 PNGs shown at 56dp — painterResource decodes each one at
 * full size on the main thread, which visibly stalled opening Settings.
 */
@Composable
fun DownsampledImage(@DrawableRes resId: Int, size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, resId, sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeDownsampled(context.resources, resId, sizePx)?.asImageBitmap() }.getOrNull()
        }
    }
    val ready = bitmap
    if (ready != null) {
        Image(bitmap = ready, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier)
    }
}

@Composable
private fun BitmapOrPlaceholder(bitmap: ImageBitmap?, modifier: Modifier) {
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier)
    }
}

private fun decodeDownsampled(resources: Resources, @DrawableRes resId: Int, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, resId, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // Largest power-of-two factor that still leaves the image at least targetPx on both sides.
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
    return BitmapFactory.decodeResource(resources, resId, BitmapFactory.Options().apply { inSampleSize = sample })
}
