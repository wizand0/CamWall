package ru.wizand.camwall.presentation.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.model.CameraStatus
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraWallScreen(
    navController: NavController,
    viewModelFactory: CameraWallViewModelFactory,
    viewModel: CameraWallViewModel = viewModel(factory = viewModelFactory)
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val cameras by viewModel.cameras.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val permissionGranted = remember { mutableStateOf(false) }

    // Request permissions on startup
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionGranted.value = viewModel.checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionGranted.value = true
        }

        if (!permissionGranted.value) {
            viewModel.requestNotificationPermission()
        }

        viewModel.loadCameras()

        val intervalSeconds = viewModel.getRefreshInterval()
        viewModel.startForegroundAutoRefresh(intervalSeconds)
    }

    // Останавливаем быстрый цикл, когда экран стены уходит с композиции
    // (например, пользователь перешёл в детали камеры или настройки),
    // чтобы не дублировать обновления и не тратить батарею впустую.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopForegroundAutoRefresh() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("CamWall") },
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshAllCameras()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        navController.navigate("settings")
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_camera") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Camera")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameras.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No cameras added yet",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the + button to add your first camera",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cameras) { camera ->
                        CameraCard(
                            camera = camera,
                            onCameraClick = { clickedCamera ->
                                navController.navigate("camera_detail/${clickedCamera.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraCard(
    camera: Camera,
    onCameraClick: (Camera) -> Unit
) {
    val context = LocalContext.current
    val frameFile = context.filesDir.resolve(camera.frameFilePath)
    val frameModel: Any? = if (frameFile.exists()) frameFile else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onCameraClick(camera) }
                .padding(8.dp)
        ) {
            // Camera thumbnail
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = frameModel,
                    contentDescription = "Camera preview",
                    modifier = Modifier
                        .fillMaxSize()
                )

                // Status indicator
                val statusColor = when (camera.status) {
                    CameraStatus.ONLINE -> Color.Green
                    CameraStatus.ERROR, CameraStatus.OFFLINE -> Color.Red
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, shape = CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Camera name and info
            Text(
                text = camera.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val frameTime = camera.lastSuccessfulFrameAt?.let {
                    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: "No frame yet"
                Text(
                    text = frameTime,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
