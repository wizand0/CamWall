package ru.wizand.camwall.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import ru.wizand.camwall.rtsp.RtspLiveViewer
import ru.wizand.camwall.util.RtspUrlMasker
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LIVE_POLL_INTERVAL_MS = 100L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetailScreen(
    navController: NavController,
    cameraId: String,
    viewModelFactory: CameraWallViewModelFactory,
    viewModel: CameraWallViewModel = viewModel(factory = viewModelFactory)
) {
    val camera by viewModel.getCameraById(cameraId).collectAsState(initial = null)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Этап C: live view через долгоживущий FFmpeg-сеанс (каждый кадр потока).
    val liveViewer = remember { RtspLiveViewer(context.applicationContext) }
    var liveMode by remember { mutableStateOf(false) }
    // Тик опроса последнего кадра: смена ключа заставляет Coil перечитать файл.
    var liveTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cameraId) {
        viewModel.loadCameraById(cameraId)
    }

    // Гарантированная остановка FFmpeg-сеанса при уходе с экрана,
    // иначе RTSP-сессия и CPU продолжат работать.
    DisposableEffect(cameraId) {
        onDispose { liveViewer.stop() }
    }

    // Пока live view включён — периодически опрашиваем файл с актуальным кадром.
    LaunchedEffect(liveMode) {
        while (liveMode) {
            delay(LIVE_POLL_INTERVAL_MS)
            liveTick++
        }
    }

    fun startLiveView() {
        val cam = camera ?: return
        val url = viewModel.getRtspUrl(cam.id)
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "RTSP URL not found in secure storage", Toast.LENGTH_SHORT).show()
            return
        }
        liveViewer.start(url)
        liveMode = true
    }

    fun stopLiveView() {
        liveViewer.stop()
        liveMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(camera?.name ?: "Camera Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Этап C: запуск/остановка live view.
                    // Stop-иконки нет в базовом наборе material-icons-core,
                    // поэтому для остановки используется Close.
                    IconButton(onClick = { if (liveMode) stopLiveView() else startLiveView() }) {
                        Icon(
                            if (liveMode) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = if (liveMode) "Stop live view" else "Start live view"
                        )
                    }

                    IconButton(onClick = {
                        android.util.Log.d("CameraDetailScreen", "Refresh clicked for ${camera?.id}")
                        // Ручное обновление кадра (доступно и для отключённых камер, этап B).
                        camera?.let { viewModel.refreshCamera(it) }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    IconButton(onClick = {
                        // Navigate to edit screen
                        // For now, we'll just navigate to add screen with edit mode
                        // In a real implementation, you'd have an EditCameraScreen
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            camera?.let { cam ->
                val frameFile = context.filesDir.resolve(cam.frameFilePath)
                val frameModel: Any? = if (frameFile.exists()) frameFile else null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Camera preview: в live-режиме показываем последний кадр
                    // из последовательности frame_%05d.jpg (план v6), иначе —
                    // последний сохранённый кадр (snapshot).
                    if (liveMode) {
                        // План v6: каждый кадр — отдельный файл, появляется только
                        // после полного закрытия — Coil никогда не читает недописанный
                        // JPEG, моргание исчезает. Имя файла уникально и само по себе
                        // служит ключом кэша.
                        val liveFrame = liveViewer.latestFrameFile()
                        if (liveFrame != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(liveFrame)
                                    .memoryCacheKey(liveFrame.name)
                                    .build(),
                                contentDescription = "Live view",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Подключение…")
                            }
                        }
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    } else {
                        AsyncImage(
                            model = frameModel,
                            contentDescription = "Camera preview",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    // Camera info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(text = "Name: ${cam.name}")
                        // Пароль в URL не показываем (ТЗ §43). Сам URL берём из
                        // EncryptedSharedPreferences, а не из Room (этап 2).
                        val storedUrl = viewModel.getRtspUrl(cam.id)
                        Text(text = "URL: ${if (storedUrl != null) RtspUrlMasker.mask(storedUrl) else "—"}")
                        Text(text = "Status: ${cam.status}")
                        val frameTime = cam.lastSuccessfulFrameAt?.let {
                            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "No frame yet"
                        Text(text = "Last frame: $frameTime")

                        // Этап B: выключенная камера не участвует в автообновлении,
                        // но ручное Refresh выше остаётся доступным.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-update",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = cam.enabled,
                                onCheckedChange = { viewModel.setCameraEnabled(cam, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Camera") },
            text = { Text("Are you sure you want to delete this camera?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        camera?.let {
                            viewModel.deleteCamera(it)
                            navController.popBackStack()
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
