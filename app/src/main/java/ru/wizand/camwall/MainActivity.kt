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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.wizand.camwall.ui.theme.CamWallTheme
import ru.wizand.camwall.util.SettingsKeys
import ru.wizand.camwall.util.settingsDataStore
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory
import ru.wizand.camwall.presentation.navigation.AppNavigation
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModelFactory = CameraWallViewModelFactory(application)
        setContent {
            // Night Mode из настроек управляет тёмной темой (блок 5).
            // Читаем напрямую из DataStore: у Settings-экрана свой экземпляр
            // ViewModel, StateFlow между ними не общий.
            //
            // План v8: тема включается только если ползунок включён И текущее
            // время попадает в заданный интервал (поддерживается переход через
            // полночь). Пересчёт — мгновенно при изменении настроек и по тику
            // раз в минуту; фоновые будильники не нужны, батарея не тратится.
            val minuteTicker = flow {
                while (true) {
                    emit(Unit)
                    delay(60_000L)
                }
            }
            val darkTheme by combine(
                application.settingsDataStore.data,
                minuteTicker
            ) { prefs, _ ->
                val enabled = prefs[SettingsKeys.NIGHT_MODE_ENABLED_KEY] ?: false
                if (!enabled) {
                    false
                } else {
                    val start = prefs[SettingsKeys.NIGHT_MODE_START_KEY]
                        ?: CameraWallViewModel.DEFAULT_NIGHT_MODE_START
                    val end = prefs[SettingsKeys.NIGHT_MODE_END_KEY]
                        ?: CameraWallViewModel.DEFAULT_NIGHT_MODE_END
                    isWithinInterval(start, end)
                }
            }.collectAsState(initial = false)

            CamWallTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModelFactory = viewModelFactory)
                }
            }
        }
    }

    /**
     * План v8: попадает ли текущее время в интервал [start, end) в минутах
     * от полуночи; start > end означает переход через полночь
     * (например, 22:00–07:00).
     */
    private fun isWithinInterval(start: Int, end: Int): Boolean {
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (start <= end) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }
}
