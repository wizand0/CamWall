package ru.wizand.camwall.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.model.CameraStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCard(
    camera: Camera,
    onCameraClick: (Camera) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        onClick = { onCameraClick(camera) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = camera.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // TODO: Implement menu items
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when {
                    !camera.enabled -> Color.Gray
                    camera.lastError != null -> Color.Red
                    camera.lastSuccessfulFrameAt == null -> Color.Yellow
                    else -> Color.Green
                }
                
                val statusText = when {
                    !camera.enabled -> "DISABLED"
                    camera.lastError != null -> "ERROR"
                    camera.lastSuccessfulFrameAt == null -> "NO DATA"
                    else -> "ONLINE"
                }
                
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Preview image
            // Заглушка для изображения камеры
            // В реальной реализации здесь будет изображение из кэша
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onDraw = {
                    drawRect(Color.LightGray)
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Timestamp and error info
            val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val timestampText = if (camera.lastSuccessfulFrameAt != null) {
                val date = Date(camera.lastSuccessfulFrameAt!!)
                "Кадр: ${formatter.format(date)}"
            } else {
                "Нет данных"
            }
            
            Text(
                text = timestampText,
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            if (camera.lastError != null) {
                Text(
                    text = "⚠ Ошибка: ${camera.lastError}",
                    fontSize = 12.sp,
                    color = Color.Red
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CameraCardPreview() {
    val mockCamera = Camera(
        id = "1",
        name = "Вход в лобби - арка",
        rtspUrl = "rtsp://example.com/cam1",
        lastSuccessfulFrameAt = System.currentTimeMillis() - 30000, // 30 секунд назад
        lastError = null,
        consecutiveErrors = 0
    )
    
    CameraCard(
        camera = mockCamera,
        onCameraClick = {}
    )
}