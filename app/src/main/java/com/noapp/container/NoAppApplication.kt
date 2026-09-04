package com.noapp.container

import android.app.Application

class NoAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
