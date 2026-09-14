package com.example.dsh

import android.app.Application
import android.content.Context

class KRApplication : Application() {

    init {
        application = this
    }

    override fun attachBaseContext(base: Context) {
        DshThemeChrome.applyDefaultNightMode(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        DshThemeChrome.applyDefaultNightMode(this)
        super.onCreate()
    }

    companion object {
        lateinit var application: Application
    }
}
