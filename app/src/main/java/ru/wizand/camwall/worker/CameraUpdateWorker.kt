package ru.wizand.camwall.worker

import android.content.Context
import android.util.Log
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
            Log.d(TAG, "doWork: background camera update started")
            val startedAt = System.currentTimeMillis()
            try {
                // Без DI: зависимости создаются напрямую (Hilt удалён из проекта)
                val database = AppDatabase.getInstance(applicationContext)
                val cameraRepository = CameraRepositoryImpl(database.cameraDao())
                val rtspFrameCapture = RtspFrameCapture(applicationContext)

                // Получаем список всех камер
                val cameras = cameraRepository.getCameras().getOrNull()
                    ?: run {
                        Log.e(TAG, "doWork: failed to load cameras from DB")
                        return@withContext Result.failure()
                    }
                Log.d(TAG, "doWork: cameras loaded=${cameras.size}")

                var successCount = 0
                var errorCount = 0

                // Обновляем кадр для каждой камеры
                for (camera in cameras) {
                    if (!camera.enabled) {
                        Log.d(TAG, "doWork: camera ${camera.id} skipped (disabled)")
                        continue
                    }
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
                            successCount++
                            Log.d(TAG, "doWork: camera ${camera.id} frame updated OK")
                        } else {
                            // Отмечаем ошибку для камеры (старый кадр не трогается)
                            val updatedCamera = camera.copy(
                                lastAttemptAt = System.currentTimeMillis(),
                                lastError = result.exceptionOrNull()?.message ?: "Unknown error",
                                consecutiveErrors = camera.consecutiveErrors + 1
                            )
                            cameraRepository.updateCamera(updatedCamera)
                            errorCount++
                            Log.w(TAG, "doWork: camera ${camera.id} capture failed: ${updatedCamera.lastError}")
                        }
                    } catch (e: Exception) {
                        // Отмечаем ошибку для камеры
                        val updatedCamera = camera.copy(
                            lastAttemptAt = System.currentTimeMillis(),
                            lastError = e.message,
                            consecutiveErrors = camera.consecutiveErrors + 1
                        )
                        cameraRepository.updateCamera(updatedCamera)
                        errorCount++
                        Log.e(TAG, "doWork: camera ${camera.id} exception", e)
                    }
                }
                Log.d(
                    TAG,
                    "doWork: finished in ${System.currentTimeMillis() - startedAt} ms, " +
                        "success=$successCount, errors=$errorCount"
                )
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "doWork: unexpected failure", e)
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "camera_update_worker"
        private const val TAG = "CameraUpdateWorker"
    }
}
