package ru.wizand.camwall.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var maxRetries by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        // Load current settings
        refreshInterval = viewModel.getRefreshInterval()
        nightModeEnabled = viewModel.isNightModeEnabled()
        maxRetries = viewModel.getMaxRetries()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Night Mode",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Switch(
                checked = nightModeEnabled,
                onCheckedChange = {
                    nightModeEnabled = it
                    viewModel.setNightModeEnabled(it)
                }
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
        }
    }
}
