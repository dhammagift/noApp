package com.noapp.container

import android.app.Application

class NoAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        // A fresh process every time is itself informative: if "closes itself" always shows a
        // brand new one right after the mode-change/dispatch log lines, that's the OS killing
        // the process outright rather than just tearing down one Activity's task.
        DebugLog.log(this, "Application", "process start pid=${android.os.Process.myPid()}")
    }
}
