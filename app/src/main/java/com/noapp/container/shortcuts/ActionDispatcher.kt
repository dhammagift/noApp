package com.noapp.container.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.noapp.container.R
import com.noapp.container.model.ShortcutSlot
import com.noapp.container.model.SlotType

/** Executes one slot's target. [sharedText] is non-null only when triggered via the Sharing API. */
object ActionDispatcher {
    fun execute(context: Context, slot: ShortcutSlot, sharedText: String? = null) {
        if (!slot.isConfigured) return
        runCatching {
            val intent = when (slot.type) {
                SlotType.APP -> appIntent(context, slot.param, sharedText)
                SlotType.URL -> Intent(Intent.ACTION_VIEW, Uri.parse(resolveTemplate(slot.param, sharedText)))
                SlotType.INTENT, SlotType.CUSTOM ->
                    Intent.parseUri(resolveTemplate(slot.param, sharedText), Intent.URI_INTENT_SCHEME)
                null -> return
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.toast_launch_failed, slot.label, it.message), Toast.LENGTH_SHORT).show()
        }
    }

    /** {{word}} in a URL/Intent param is replaced with the shared text (URL-encoded). No-op if absent or no share. */
    private fun resolveTemplate(param: String, sharedText: String?): String =
        if (sharedText != null) param.replace("{{word}}", Uri.encode(sharedText)) else param

    /**
     * Forward shared text natively via ACTION_SEND if the target app can receive it
     * (e.g. a translator or notes app); otherwise fall back to a plain launch.
     */
    private fun appIntent(context: Context, packageName: String, sharedText: String?): Intent {
        if (sharedText != null) {
            val sendIntent = Intent(Intent.ACTION_SEND)
                .setPackage(packageName)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, sharedText)
            if (sendIntent.resolveActivity(context.packageManager) != null) return sendIntent
        }
        return context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("App not installed")
    }
}
