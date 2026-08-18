package ru.wizand.camwall.rtsp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ScrollableColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Тестовый экран для проверки RTSP потоков
@Composable
fun TestRtspScreen() {
    val context = LocalContext.current
    var selectedStream by remember { mutableStateOf(0) } // 0 - Земченков, 1 - Л4-43
    var isCapturing by remember { mutableStateOf(false) }
    var captureResult by remember { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    val rtspUrls = listOf(
        "rtsp://web:TnQ75giwFj7K@85.198.112.54:557/Streaming/Channels/3202", // Земченков
        "rtsp://46.138.246.136:554/user=admin_password=E16Li3O4_channel=1_stream=0&onvif=0.sdp?real_st" // Л4-43
    )
    
    val rtspCapture = remember { RtspFrameCaptureNew(context) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Тест RTSP потоков",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Выбор RTSP потока
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Выберите RTSP поток:",
                    style = MaterialTheme.typography.titleMedium
                )
                
                rtspUrls.forEachIndexed { index, url ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedStream == index,
                            onClick = { selectedStream = index }
                        )
                        Text(
                            text = if (index == 0) "Земченков" else "Л4-43",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, bottom = 8.dp)
                    )
                }
            }
        }
        
        // Кнопка захвата кадра
        Button(
            onClick = {
                isCapturing = true
                captureResult = null
                capturedBitmap = null
                
                // Запускаем захват кадра в фоне
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val result = rtspCapture.captureFrame(rtspUrls[selectedStream])
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isCapturing = false
                        if (result.isSuccess) {
                            captureResult = "Успешно захвачен кадр: ${result.getOrNull()?.width}x${result.getOrNull()?.height}"
                            capturedBitmap = result.getOrNull()?.bitmap
                        } else {
                            captureResult = "Ошибка: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
            },
            enabled = !isCapturing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCapturing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = " Захват...",
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Text("Захватить кадр")
            }
        }
        
        // Результат захвата
        if (captureResult != null) {
            Card {
                Text(
                    text = captureResult!!,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Отображение захваченного кадра
        if (capturedBitmap != null) {
            Text(
                text = "Захваченный кадр:",
                style = MaterialTheme.typography.titleMedium
            )
            Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Captured frame",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f/9f)
            )
        }
    }
}