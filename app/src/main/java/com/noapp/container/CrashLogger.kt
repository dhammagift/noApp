package com.noapp.container

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Writes the last uncaught exception to a file under app-specific external storage — visible
 * via any file manager at Android/data/gift.dhamma.noapp/files/last_crash.txt, no root or ADB
 * needed — so a crash can be reported from a phone alone. Installed once from
 * [NoAppApplication.onCreate], so it also catches crashes that happen before MainActivity ever
 * gets a chance to run (e.g. during the launcher-icon reconciliation on a cold start).
 */
object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                file(appContext).writeText(
                    "Not App crash — ${Date()}\n\n${Log.getStackTraceString(throwable)}"
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
}
