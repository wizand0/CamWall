package ru.wizand.camwall.presentation.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.util.RtspUrlMasker
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory
import java.io.File
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
    var showEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // План v7: live-сеанс живёт в ViewModel (переживает повороты Activity),
    // экран только читает состояние и последние кадры.
    val liveMode by viewModel.isLiveActive.collectAsState()
    // Тик опроса последнего кадра: смена ключа заставляет Coil перечитать файл.
    var liveTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cameraId) {
        viewModel.loadCameraById(cameraId)
    }

    // Пока live view включён — периодически опрашиваем последний кадр.
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
        viewModel.startLiveView(cam.id)
    }

    fun stopLiveView() {
        viewModel.stopLiveView()
    }

    // План v7: live + альбом → трансляция на весь экран без TopAppBar,
    // управление — плавающая кнопка поверх видео.
    if (liveMode && isLandscape) {
        FullscreenLiveLayout(
            liveFrame = viewModel.latestLiveFrame(),
            liveTick = liveTick,
            onStop = { stopLiveView() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(camera?.name ?: "Camera Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { if (liveMode) stopLiveView() else startLiveView() }) {
                        Icon(
                            if (liveMode) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = if (liveMode) "Stop live view" else "Start live view"
                        )
                    }

                    IconButton(onClick = {
                        // Ручное обновление кадра (доступно и для отключённых камер, этап B).
                        camera?.let { viewModel.refreshCamera(it) }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    IconButton(onClick = { showEditDialog = true }) {
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
                if (isLandscape) {
                    // План v7: альбом без live — кадр слева на пол-экрана,
                    // справа колонка с информацией.
                    LandscapeDetailLayout(
                        camera = cam,
                        liveMode = liveMode,
                        liveFrame = viewModel.latestLiveFrame(),
                        liveTick = liveTick,
                        maskedUrl = viewModel.getRtspUrl(cam.id)?.let(RtspUrlMasker::mask),
                        onToggleEnabled = { viewModel.setCameraEnabled(cam, it) }
                    )
                } else {
                    PortraitDetailLayout(
                        camera = cam,
                        liveMode = liveMode,
                        liveFrame = viewModel.latestLiveFrame(),
                        liveTick = liveTick,
                        maskedUrl = viewModel.getRtspUrl(cam.id)?.let(RtspUrlMasker::mask),
                        onToggleEnabled = { viewModel.setCameraEnabled(cam, it) }
                    )
                }
            }
        }
    }

    // Диалог редактирования (план v7): имя — в Room, URL — в
    // EncryptedSharedPreferences. Пустой URL сохраняет текущий.
    if (showEditDialog) {
        EditCameraDialog(
            camera = camera,
            onDismiss = { showEditDialog = false },
            onSave = { name, url ->
                camera?.let { viewModel.updateCamera(it, name, url) }
                showEditDialog = false
            }
        )
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

/**
 * Диалог редактирования камеры: имя (обязательно) и RTSP URL (опционально —
 * пустое поле сохраняет текущий URL). Пароль в UI не показываем: поле URL
 * изначально пустое, текущее значение не подставляется.
 */
@Composable
private fun EditCameraDialog(
    camera: Camera?,
    onDismiss: () -> Unit,
    onSave: (name: String, rtspUrl: String?) -> Unit
) {
    var name by remember(camera?.id) { mutableStateOf(camera?.name.orEmpty()) }
    var rtspUrl by remember(camera?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Camera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rtspUrl,
                    onValueChange = { rtspUrl = it },
                    label = { Text("RTSP URL (leave empty to keep current)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, rtspUrl) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * План v7: live-трансляция на весь экран (альбом). Кнопка остановки —
 * плавающая поверх видео, чтобы не терять полезную площадь.
 */
@Composable
private fun FullscreenLiveLayout(
    liveFrame: File?,
    liveTick: Long,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LivePreview(
            liveFrame = liveFrame,
            liveTick = liveTick,
            modifier = Modifier.fillMaxSize()
        )

        // Индикатор LIVE + кнопка остановки поверх видео.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Red,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(
                onClick = onStop,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Stop live view", tint = Color.White)
            }
        }
    }
}

/**
 * План v7: альбомная раскладка без live — кадр слева (~пол-экрана),
 * справа информационная колонка.
 */
@Composable
private fun LandscapeDetailLayout(
    camera: Camera,
    liveMode: Boolean,
    liveFrame: File?,
    liveTick: Long,
    maskedUrl: String?,
    onToggleEnabled: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (liveMode) {
                LivePreview(
                    liveFrame = liveFrame,
                    liveTick = liveTick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SnapshotPreview(camera = camera, modifier = Modifier.fillMaxSize())
            }
        }

        CameraInfoPanel(
            camera = camera,
            maskedUrl = maskedUrl,
            onToggleEnabled = onToggleEnabled,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        )
    }
}

/**
 * Портретная раскладка (как было): кадр сверху, информация снизу.
 */
@Composable
private fun PortraitDetailLayout(
    camera: Camera,
    liveMode: Boolean,
    liveFrame: File?,
    liveTick: Long,
    maskedUrl: String?,
    onToggleEnabled: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (liveMode) {
                LivePreview(
                    liveFrame = liveFrame,
                    liveTick = liveTick,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 4.dp)
                )
            } else {
                SnapshotPreview(camera = camera, modifier = Modifier.fillMaxSize())
            }
        }

        CameraInfoPanel(
            camera = camera,
            maskedUrl = maskedUrl,
            onToggleEnabled = onToggleEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Live-превью: последний полностью записанный кадр из последовательности
 * frame_%05d.jpg (план v6). Пока кадров нет — «Подключение…».
 */
@Composable
private fun LivePreview(
    liveFrame: File?,
    liveTick: Long,
    modifier: Modifier = Modifier
) {
    if (liveFrame != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(liveFrame)
                // Имя файла уникально и само по себе служит ключом кэша;
                // тик добавлен на случай совпадения имён после очистки каталога.
                .memoryCacheKey("${liveFrame.name}-$liveTick")
                .build(),
            contentDescription = "Live view",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Подключение…")
        }
    }
}

/**
 * Snapshot-превью: последний сохранённый кадр камеры.
 * План v7, проблема 3: memoryCacheKey меняется с каждым успешным захватом
 * (lastSuccessfulFrameAt), иначе Coil отдаёт старый кадр из кэша по
 * неизменному пути latest.jpg.
 */
@Composable
private fun SnapshotPreview(camera: Camera, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frameFile = context.filesDir.resolve(camera.frameFilePath)
    if (frameFile.exists()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(frameFile)
                .memoryCacheKey("${camera.id}-${camera.lastSuccessfulFrameAt ?: 0}")
                .build(),
            contentDescription = "Camera preview",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No frame yet")
        }
    }
}

/**
 * Информационная панель: имя, URL (маскированный), статус, время кадра,
 * переключатель автообновления.
 */
@Composable
private fun CameraInfoPanel(
    camera: Camera,
    maskedUrl: String?,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "Name: ${camera.name}")
        // Пароль в URL не показываем (ТЗ §43). Сам URL берётся из
        // EncryptedSharedPreferences, а не из Room (этап 2).
        Text(text = "URL: ${maskedUrl ?: "—"}")
        Text(text = "Status: ${camera.status}")
        val frameTime = camera.lastSuccessfulFrameAt?.let {
            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "No frame yet"
        Text(text = "Last frame: $frameTime")

        // Этап B: выключенная камера не участвует в автообновлении,
        // но ручное Refresh остаётся доступным.
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
                checked = camera.enabled,
                onCheckedChange = onToggleEnabled
            )
        }
    }
}
