package ru.wizand.camwall.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModelFactory: CameraWallViewModelFactory,
    viewModel: CameraWallViewModel = viewModel(factory = viewModelFactory)
) {
    var refreshInterval by remember { mutableIntStateOf(30) }
    var nightModeEnabled by remember { mutableStateOf(false) }
    var nightModeStart by remember { mutableIntStateOf(CameraWallViewModel.DEFAULT_NIGHT_MODE_START) }
    var nightModeEnd by remember { mutableIntStateOf(CameraWallViewModel.DEFAULT_NIGHT_MODE_END) }
    var backgroundUpdateEnabled by remember { mutableStateOf(true) }
    var maxRetries by remember { mutableIntStateOf(3) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Load current settings
        refreshInterval = viewModel.getRefreshInterval()
        nightModeEnabled = viewModel.isNightModeEnabled()
        nightModeStart = viewModel.getNightModeStart()
        nightModeEnd = viewModel.getNightModeEnd()
        backgroundUpdateEnabled = viewModel.isBackgroundUpdateEnabled()
        maxRetries = viewModel.getMaxRetries()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- План v8: фоновое автообновление ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Background auto-update")
                Switch(
                    checked = backgroundUpdateEnabled,
                    onCheckedChange = {
                        backgroundUpdateEnabled = it
                        viewModel.setBackgroundUpdateEnabled(it)
                    }
                )
            }
            Text(
                text = "Периодически обновляет кадры камер в фоне (минимум раз в " +
                        "15 минут — ограничение Android). Отключите, чтобы сэкономить " +
                        "батарею и трафик.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // --- План v8: Night Mode по расписанию ---
            // Секция активна только при включённом фоновом автообновлении:
            // без фона night mode не имеет смысла (по требованию пользователя).
            val nightSectionEnabled = backgroundUpdateEnabled
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (nightSectionEnabled) 1f else 0.38f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Night Mode")
                    Switch(
                        checked = nightModeEnabled,
                        enabled = nightSectionEnabled,
                        onCheckedChange = {
                            nightModeEnabled = it
                            viewModel.setNightModeEnabled(it)
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = nightSectionEnabled,
                        onClick = { showStartPicker = true }
                    ) {
                        Text("Start: ${formatMinutes(nightModeStart)}")
                    }
                    OutlinedButton(
                        enabled = nightSectionEnabled,
                        onClick = { showEndPicker = true }
                    ) {
                        Text("End: ${formatMinutes(nightModeEnd)}")
                    }
                }
                Text(
                    text = "Тёмная тема по расписанию. Автоматически включается в заданное " +
                            "время, чтобы меньше расходовать батарею на AMOLED-экранах. " +
                            "Интервал может переходить через полночь.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Text(
                text = "Refresh Interval (seconds)",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Slider(
                value = refreshInterval.toFloat(),
                onValueChange = { refreshInterval = it.toInt() },
                onValueChangeFinished = { viewModel.applyRefreshInterval(refreshInterval) },
                valueRange = 5f..300f,
                steps = 58 // Divides range into 5-second increments
            )

            Text(
                text = "$refreshInterval seconds",
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Applies while the app is open. Background updates " +
                        "(app closed) run at least every 15 minutes — an Android " +
                        "system limitation.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Max Retries Per Camera",
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Slider(
                value = maxRetries.toFloat(),
                onValueChange = { maxRetries = it.toInt() },
                onValueChangeFinished = { viewModel.setMaxRetries(maxRetries) },
                valueRange = 1f..10f,
                steps = 8
            )

            Text(
                text = "$maxRetries retries",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Диагностика (ТЗ §44): ручной запуск фоновой WorkManager-задачи,
            // чтобы подтвердить, что обновление кадров работает вне foreground-цикла.
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            OutlinedButton(
                onClick = { viewModel.triggerOneTimeUpdate() }
            ) {
                Text("Trigger background update")
            }
            Text(
                text = "Enqueues a one-time WorkManager job that refreshes all " +
                        "camera frames exactly like the periodic background task. " +
                        "Check logcat (tags: CameraUpdateWorker, RtspFrameCapture).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // --- План v8: выбор времени начала/конца night mode ---
    if (showStartPicker) {
        TimePickerDialog(
            title = "Night Mode start",
            initialMinutes = nightModeStart,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                nightModeStart = it
                viewModel.setNightModeStart(it)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            title = "Night Mode end",
            initialMinutes = nightModeEnd,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                nightModeEnd = it
                viewModel.setNightModeEnd(it)
                showEndPicker = false
            }
        )
    }
}

/** Минуты от полуночи -> "HH:mm". */
private fun formatMinutes(minutes: Int): String =
    String.format("%02d:%02d", minutes / 60, minutes % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
