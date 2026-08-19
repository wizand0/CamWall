package ru.wizand.camwall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.map
import ru.wizand.camwall.ui.theme.CamWallTheme
import ru.wizand.camwall.util.SettingsKeys
import ru.wizand.camwall.util.settingsDataStore
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory
import ru.wizand.camwall.presentation.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModelFactory = CameraWallViewModelFactory(application)
        setContent {
            // Night Mode из настроек управляет тёмной темой (блок 5).
            // Читаем напрямую из DataStore: у Settings-экрана свой экземпляр
            // ViewModel, StateFlow между ними не общий.
            val nightMode by application.settingsDataStore.data
                .map { it[SettingsKeys.NIGHT_MODE_ENABLED_KEY] ?: false }
                .collectAsState(initial = false)
            CamWallTheme(darkTheme = nightMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModelFactory = viewModelFactory)
                }
            }
        }
    }
}