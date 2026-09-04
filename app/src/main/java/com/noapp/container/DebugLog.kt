package com.noapp.container

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/**
 * An always-on trail of what the app did and when — not Android's system logcat (a third-party
 * app can't read that without a signature-level permission), but our own record of lifecycle
 * calls and key decisions, kept across the whole app's life so a "closes on its own, no crash"
 * report can be diagnosed straight from the phone. See Settings > Debug log for exporting it,
 * and CrashLogger for how an actual uncaught exception also lands in here.
 *
 * Writes go through one background thread (in order), so the half-dozen lines every launch
 * produces never cost file I/O on the main thread — the launch path is the one place this log
 * exists to observe, and it shouldn't slow it down. [read] queues behind pending writes on the
 * same thread, so it always sees everything logged before it was called.
 */
object DebugLog {
    private const val FILE_NAME = "debug_log.txt"
    private const val MAX_SIZE_BYTES = 300_000
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "DebugLog") }

    fun log(context: Context, tag: String, message: String) {
        val appContext = context.applicationContext
        val line = "${DateFormat.format("MM-dd HH:mm:ss.SSS", System.currentTimeMillis())} [$tag] $message\n"
        executor.execute { runCatching { append(appContext, line) } }
    }

    /**
     * Same as [log] but written before returning — for the crash handler, where the process is
     * about to die and a queued write would be lost with it.
     */
    fun logNow(context: Context, tag: String, message: String) {
        val line = "${DateFormat.format("MM-dd HH:mm:ss.SSS", System.currentTimeMillis())} [$tag] $message\n"
        runCatching { append(context.applicationContext, line) }
    }

    fun read(context: Context): String =
        runCatching { executor.submit<String> { file(context).readText() }.get() }.getOrDefault("")

    fun clear(context: Context) {
        executor.execute { runCatching { file(context).delete() } }
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

    /** Same log file, pre-addressed to an email — "message/rfc822" targets mail apps specifically. */
    fun emailIntent(context: Context, to: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(context))
        return Intent(Intent.ACTION_SEND)
            .setType("message/rfc822")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun file(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    private fun append(context: Context, line: String) {
        val f = file(context)
        f.appendText(line)
        if (f.length() > MAX_SIZE_BYTES) {
            f.writeText(f.readText().takeLast(MAX_SIZE_BYTES / 2))
        }
    }
}
