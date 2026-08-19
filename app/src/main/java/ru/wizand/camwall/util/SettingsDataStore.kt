package ru.wizand.camwall.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Единый DataStore настроек приложения.
 * Объявлен в одном месте, чтобы не создавать несколько экземпляров
 * preferencesDataStore с одним именем (это приводит к IllegalStateException).
 */
val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val REFRESH_INTERVAL_KEY = intPreferencesKey("refresh_interval")
    val NIGHT_MODE_ENABLED_KEY = booleanPreferencesKey("night_mode_enabled")
    val MAX_RETRIES_KEY = intPreferencesKey("max_retries")
}
