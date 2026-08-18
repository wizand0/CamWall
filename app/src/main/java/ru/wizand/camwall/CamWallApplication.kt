package ru.wizand.camwall

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CamWallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}