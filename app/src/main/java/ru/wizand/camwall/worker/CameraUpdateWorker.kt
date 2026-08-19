package ru.wizand.camwall.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.wizand.camwall.data.local.database.AppDatabase
import ru.wizand.camwall.data.repository.CameraRepositoryImpl
import ru.wizand.camwall.rtsp.RtspFrameCapture

class CameraUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Без DI: зависимости создаются напрямую (Hilt удалён из проекта)
                val database = AppDatabase.getInstance(applicationContext)
                val cameraRepository = CameraRepositoryImpl(database.cameraDao())
                val rtspFrameCapture = RtspFrameCapture(applicationContext)

                // Получаем список всех камер
                val cameras = cameraRepository.getCameras().getOrNull() ?: return@withContext Result.failure()

                // Обновляем кадр для каждой камеры
                for (camera in cameras) {
                    if (camera.enabled) { // Только если камера включена
                        try {
                            val result = rtspFrameCapture.captureFrame(camera.id, camera.rtspUrl)
                            if (result.isSuccess) {
                                // Обновляем информацию о камере с новым кадром
                                val updatedCamera = camera.copy(
                                    lastSuccessfulFrameAt = System.currentTimeMillis(),
                                    lastAttemptAt = System.currentTimeMillis(),
                                    lastError = null,
                                    consecutiveErrors = 0
                                )
                                cameraRepository.updateCamera(updatedCamera)
                            } else {
                                // Отмечаем ошибку для камеры (старый кадр не трогается)
                                val updatedCamera = camera.copy(
                                    lastAttemptAt = System.currentTimeMillis(),
                                    lastError = result.exceptionOrNull()?.message ?: "Unknown error",
                                    consecutiveErrors = camera.consecutiveErrors + 1
                                )
                                cameraRepository.updateCamera(updatedCamera)
                            }
                        } catch (e: Exception) {
                            // Отмечаем ошибку для камеры
                            val updatedCamera = camera.copy(
                                lastAttemptAt = System.currentTimeMillis(),
                                lastError = e.message,
                                consecutiveErrors = camera.consecutiveErrors + 1
                            )
                            cameraRepository.updateCamera(updatedCamera)
                        }
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "camera_update_worker"
    }
}
