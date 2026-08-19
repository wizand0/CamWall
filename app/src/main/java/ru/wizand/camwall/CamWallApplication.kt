package ru.wizand.camwall

import android.app.Application

class CamWallApplication : Application() {
    companion object {
        lateinit var instance: CamWallApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}