package com.noapp.container

import android.content.Context
import android.util.Log

/**
 * Catches uncaught exceptions and feeds them into DebugLog, plus flags that the very next
 * launch should show the log full-screen instead of proceeding normally. If the app disappears
 * without ever setting this flag, whatever is closing it isn't a JVM exception at all (nothing
 * for an UncaughtExceptionHandler to catch) — that's diagnostic in itself; see DebugLog's own
 * lifecycle entries for what actually happened instead.
 */
object CrashLogger {
    private const val PREFS_NAME = "crash_logger_prefs"
    private const val KEY_PENDING_CRASH = "pending_crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                DebugLog.log(appContext, "CRASH", Log.getStackTraceString(throwable))
                prefs(appContext).edit().putBoolean(KEY_PENDING_CRASH, true).apply()
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** True at most once per crash — clears the flag as it reads it. */
    fun consumePendingCrash(context: Context): Boolean {
        val p = prefs(context)
        val pending = p.getBoolean(KEY_PENDING_CRASH, false)
        if (pending) p.edit().putBoolean(KEY_PENDING_CRASH, false).apply()
        return pending
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
