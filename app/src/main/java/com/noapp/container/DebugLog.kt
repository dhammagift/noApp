package com.noapp.container

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import java.io.File

/**
 * An always-on trail of what the app did and when — not Android's system logcat (a third-party
 * app can't read that without a signature-level permission), but our own record of lifecycle
 * calls and key decisions, kept across the whole app's life so a "closes on its own, no crash"
 * report can be diagnosed straight from the phone. See Settings > Debug log for exporting it,
 * and CrashLogger for how an actual uncaught exception also lands in here.
 */
object DebugLog {
    private const val FILE_NAME = "debug_log.txt"
    private const val MAX_SIZE_BYTES = 300_000

    fun log(context: Context, tag: String, message: String) {
        runCatching {
            val line = "${DateFormat.format("MM-dd HH:mm:ss.SSS", System.currentTimeMillis())} [$tag] $message\n"
            file(context).appendText(line)
            trimIfNeeded(context)
        }
    }

    fun read(context: Context): String = runCatching { file(context).readText() }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** A chooser Intent attaching the log file itself — works even once it's too big to paste. */
    fun shareIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(context))
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    fun file(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    private fun trimIfNeeded(context: Context) {
        val f = file(context)
        if (f.length() > MAX_SIZE_BYTES) {
            val kept = f.readText().takeLast(MAX_SIZE_BYTES / 2)
            f.writeText(kept)
        }
    }
}
