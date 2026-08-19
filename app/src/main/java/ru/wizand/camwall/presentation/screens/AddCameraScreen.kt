package ru.wizand.camwall.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCameraScreen(
    navController: NavController,
    viewModelFactory: CameraWallViewModelFactory,
    viewModel: CameraWallViewModel = viewModel(factory = viewModelFactory)
) {
    var name by remember { mutableStateOf("") }
    var rtspUrl by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Этап A: результат сканирования QR возвращается через savedStateHandle
    // текущего back stack entry (ScanQrScreen пишет туда и делает popBackStack).
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle
            ?.getStateFlow<String?>(SCAN_RESULT_KEY, null)
            ?.collect { scannedUrl ->
                if (scannedUrl != null) {
                    rtspUrl = scannedUrl
                    testResult = "QR code scanned — check the URL and add a name"
                    navController.currentBackStackEntry?.savedStateHandle
                        ?.remove<String>(SCAN_RESULT_KEY)
                }
            }
    }

    val qrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            navController.navigate("scan_qr")
        } else {
            testResult = "Camera permission is required to scan QR codes"
        }
    }
    fun openQrScanner() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) navController.navigate("scan_qr") else qrLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Camera") },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Camera Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = rtspUrl,
                onValueChange = { rtspUrl = it },
                label = { Text("RTSP URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            // Этап A: добавление камеры по QR-коду (plain-text rtsp:// ссылка).
            OutlinedButton(
                onClick = { openQrScanner() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Scan QR Code")
            }

            Button(
                onClick = {
                    // Test the RTSP URL
                    viewModel.testRtspConnection(rtspUrl) { success ->
                        if (success) {
                            testResult = "Connection successful!"
                        } else {
                            testResult = "Connection failed"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Test")
                    Text(text = "Test Connection", modifier = Modifier.padding(start = 8.dp))
                }
            }

            testResult?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (name.isNotEmpty() && rtspUrl.isNotEmpty()) {
                        viewModel.addCamera(name, rtspUrl)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Save Camera")
            }
        }
    }
}
