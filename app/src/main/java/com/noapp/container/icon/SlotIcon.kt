package com.noapp.container.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType

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

@Composable
fun SlotIcon(slot: ShortcutSlot, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    if (!slot.isConfigured) {
        Text("?", modifier = modifier.size(size))
        return
    }
    val bitmap = remember(slot.type, slot.param, slot.label, slot.color, slot.customIcon) {
        iconBitmapFor(context, slot, sizePx).asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
}

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap = remember(packageName) { appIconBitmap(context, packageName, sizePx).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
}
