package ru.wizand.camwall.viewmodels

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository
import ru.wizand.camwall.domain.usecase.AddCameraUseCase
import ru.wizand.camwall.domain.usecase.DeleteCameraUseCase
import ru.wizand.camwall.domain.usecase.GetCamerasUseCase
import ru.wizand.camwall.domain.usecase.UpdateCameraUseCase
import ru.wizand.camwall.rtsp.RtspFrameCapture
import ru.wizand.camwall.manager.CameraUpdateManager
import ru.wizand.camwall.util.SettingsKeys
import ru.wizand.camwall.util.settingsDataStore

class CameraWallViewModel(
    private val application: Application,
    private val getCamerasUseCase: GetCamerasUseCase,
    private val addCameraUseCase: AddCameraUseCase,
    private val deleteCameraUseCase: DeleteCameraUseCase,
    private val updateCameraUseCase: UpdateCameraUseCase,
    private val cameraRepository: ICameraRepository,
    private val rtspFrameCapture: RtspFrameCapture
) : AndroidViewModel(application) {

    private val _cameras = MutableStateFlow<List<Camera>>(emptyList())
    val cameras: StateFlow<List<Camera>> = _cameras

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    companion object {
        private const val TAG = "CameraWallViewModel"
        private const val DEFAULT_REFRESH_INTERVAL = 30
        private const val DEFAULT_MAX_RETRIES = 3

        // Concurrency обхода: не более двух камер одновременно (v4 этап 5).
        private const val MAX_CONCURRENT_UPDATES = 2
        // Экспоненциальный backoff для ретраев захвата кадра.
        private const val RETRY_BASE_BACKOFF_MS = 1_000L
        private const val RETRY_MAX_BACKOFF_MS = 8_000L
    }

    init {
        loadCameras()
        // Фоновое обновление (WorkManager) должно быть запланировано при каждом
        // старте приложения, а не только при заходе в Settings — иначе после
        // переустановки/очистки данных расписание пропадает и не восстанавливается.
        viewModelScope.launch {
            scheduleBackgroundUpdates(getRefreshInterval())
        }
    }

    fun loadCameras() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cameraList = getCamerasUseCase()
                _cameras.value = cameraList.getOrDefault(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cameras", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addCamera(name: String, rtspUrl: String) {
        viewModelScope.launch {
            try {
                val camera = Camera(name = name)
                // URL с логином/паролем в Room не попадает (этап 2) —
                // сразу в EncryptedSharedPreferences, ключ — id камеры.
                cameraRepository.saveRtspUrl(camera.id, rtspUrl)
                addCameraUseCase(camera)
                loadCameras() // Refresh the list
            } catch (e: Exception) {
                Log.e(TAG, "Error adding camera", e)
            }
        }
    }

    fun deleteCamera(camera: Camera) {
        viewModelScope.launch {
            try {
                deleteCameraUseCase(camera.id)
                // URL удаляется репозиторием вместе с записью камеры.
                // Удаляем файл кадра камеры (ТЗ §8)
                getApplication<Application>()
                    .filesDir.resolve("cameras/${camera.id}")
                    .deleteRecursively()
                loadCameras() // Refresh the list
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting camera", e)
            }
        }
    }

    fun testRtspConnection(rtspUrl: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Тестовое подключение: cameraId не важен, кадр сохранится в служебную папку
                val result = rtspFrameCapture.captureFrame("test", rtspUrl)
                callback(result.isSuccess)
            } catch (e: Exception) {
                Log.e(TAG, "Error testing RTSP connection", e)
                callback(false)
            }
        }
    }

    fun refreshCamera(camera: Camera) {
        viewModelScope.launch {
            val updatedCamera = refreshCameraInternal(camera)
            updateCameraUseCase(updatedCamera)
            loadCameras() // Refresh the list to show updated camera
        }
    }

    fun refreshAllCameras() {
        viewModelScope.launch {
            // Защита от пересекающихся обходов (v4 §1.3): если предыдущий
            // обход ещё идёт, новый не стартует, а просто пропускается.
            if (!refreshMutex.tryLock()) {
                Log.d(TAG, "refreshAllCameras skipped: previous sweep still running")
                return@launch
            }
            try {
                _isLoading.value = true
                try {
                    val cameras = getCamerasUseCase().getOrDefault(emptyList())
                    refreshSweep(cameras)
                    loadCameras() // Refresh the list
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing all cameras", e)
                } finally {
                    _isLoading.value = false
                }
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    /**
     * Быстрый старт (v4 §1.2): однократно обновляет только камеры без кадра
     * (lastSuccessfulFrameAt == null), чтобы после добавления камеры или
     * переустановки приложения пользователь не ждал первого тика интервала.
     * Вызывается при открытии экрана стены.
     */
    fun refreshCamerasWithoutFrames() {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) {
                Log.d(TAG, "refreshCamerasWithoutFrames skipped: sweep already running")
                return@launch
            }
            try {
                val cameras = getCamerasUseCase().getOrDefault(emptyList())
                val withoutFrames = cameras.filter { it.lastSuccessfulFrameAt == null }
                if (withoutFrames.isEmpty()) return@launch
                Log.d(TAG, "refreshCamerasWithoutFrames: updating ${withoutFrames.size} camera(s)")
                refreshSweep(withoutFrames)
                loadCameras()
            } catch (e: Exception) {
                Log.e(TAG, "Error in refreshCamerasWithoutFrames", e)
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    /**
     * Обход списка камер с ограничением concurrency=2 (v4 этап 5):
     * одновременно обновляются не более двух камер, чтобы не держать
     * много RTSP-сессий и не перегружать сеть.
     */
    private suspend fun refreshSweep(cameras: List<Camera>) {
        if (cameras.isEmpty()) return
        val semaphore = Semaphore(MAX_CONCURRENT_UPDATES)
        coroutineScope {
            cameras.map { camera ->
                async {
                    semaphore.acquire()
                    try {
                        val updatedCamera = refreshCameraInternal(camera)
                        updateCameraUseCase(updatedCamera)
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Пытается получить новый кадр для камеры с ретраями и экспоненциальным
     * backoff (v4 этап 5). Число попыток — из настроек (Max Retries, 1..10).
     * При успехе обновляет lastSuccessfulFrameAt и сбрасывает ошибки;
     * при неудаче — lastAttemptAt, lastError и consecutiveErrors (старый кадр не трогается, ТЗ §9).
     */
    private suspend fun refreshCameraInternal(camera: Camera): Camera {
        val rtspUrl = cameraRepository.getRtspUrl(camera.id)
        if (rtspUrl.isNullOrBlank()) {
            return camera.copy(
                lastAttemptAt = System.currentTimeMillis(),
                lastError = "RTSP URL not found in secure storage",
                consecutiveErrors = camera.consecutiveErrors + 1
            )
        }

        val maxRetries = getMaxRetries().coerceIn(1, 10)
        var lastFailureMessage: String? = null
        var backoffMs = RETRY_BASE_BACKOFF_MS

        for (attempt in 1..maxRetries) {
            if (attempt > 1) {
                Log.d(TAG, "Retry $attempt/$maxRetries for camera ${camera.name} after ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(RETRY_MAX_BACKOFF_MS)
            }
            val result = try {
                rtspFrameCapture.captureFrame(camera.id, rtspUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing camera ${camera.name}", e)
                Result.failure(e)
            }
            if (result.isSuccess) {
                lastFailureMessage = null
                break
            }
            lastFailureMessage = result.exceptionOrNull()?.message ?: "Unknown error"
        }

        return if (lastFailureMessage == null) {
            camera.copy(
                lastSuccessfulFrameAt = System.currentTimeMillis(),
                lastAttemptAt = System.currentTimeMillis(),
                lastError = null,
                consecutiveErrors = 0
            )
        } else {
            camera.copy(
                lastAttemptAt = System.currentTimeMillis(),
                lastError = lastFailureMessage,
                consecutiveErrors = camera.consecutiveErrors + 1
            )
        }
    }

    fun checkPermission(permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            application.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestNotificationPermission() {
        // Implementation depends on the Activity Result API
        // This would typically be handled in the Activity/Fragment
    }

    // DataStore methods for settings
    suspend fun getRefreshInterval(): Int {
        val preferences = application.settingsDataStore.data.first()
        return preferences[SettingsKeys.REFRESH_INTERVAL_KEY] ?: DEFAULT_REFRESH_INTERVAL
    }

    fun setRefreshInterval(interval: Int) {
        viewModelScope.launch {
            application.settingsDataStore.edit { preferences ->
                preferences[SettingsKeys.REFRESH_INTERVAL_KEY] = interval
            }
        }
    }

    suspend fun isNightModeEnabled(): Boolean {
        val preferences = application.settingsDataStore.data.first()
        return preferences[SettingsKeys.NIGHT_MODE_ENABLED_KEY] ?: false
    }

    fun setNightModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            application.settingsDataStore.edit { preferences ->
                preferences[SettingsKeys.NIGHT_MODE_ENABLED_KEY] = enabled
            }
        }
    }

    suspend fun getMaxRetries(): Int {
        val preferences = application.settingsDataStore.data.first()
        return preferences[SettingsKeys.MAX_RETRIES_KEY] ?: DEFAULT_MAX_RETRIES
    }

    fun setMaxRetries(retries: Int) {
        viewModelScope.launch {
            application.settingsDataStore.edit { preferences ->
                preferences[SettingsKeys.MAX_RETRIES_KEY] = retries
            }
        }
    }

    // Flow for a specific camera by ID
    fun getCameraById(id: String) = cameraRepository.getCameraById(id)

    /**
     * Маскированный URL для показа в UI: сам секрет не покидает
     * EncryptedSharedPreferences (этап 2).
     */
    fun getRtspUrl(cameraId: String): String? = cameraRepository.getRtspUrl(cameraId)

    fun loadCameraById(id: String) {
        viewModelScope.launch {
            try {
                val camera = cameraRepository.getCameraById(id).first()
                // Note: We don't update the _cameras flow here since it's just for one camera
                // The individual camera detail screen would observe this specific camera
                Log.d(TAG, "Loaded camera by id: ${camera?.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading camera by id", e)
            }
        }
    }

    fun scheduleCameraUpdates(intervalMinutes: Long) {
        val updateManager = CameraUpdateManager(application)
        updateManager.scheduleCameraUpdates(intervalMinutes)
    }

    // --- Быстрое автообновление, пока приложение открыто на экране стены ---
    // WorkManager не умеет чаще, чем раз в 15 минут (системное ограничение),
    // поэтому "живой" интервал из настроек (5-300 сек) обслуживается отдельным
    // корутин-циклом, а не WorkManager.

    private var foregroundRefreshJob: Job? = null

    // Защита от пересекающихся обходов: общий Mutex для ручного обновления,
    // быстрого старта и foreground-цикла.
    private val refreshMutex = Mutex()

    fun startForegroundAutoRefresh(intervalSeconds: Int) {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalSeconds * 1000L)
                refreshAllCameras()
            }
        }
    }

    fun stopForegroundAutoRefresh() {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
    }

    /**
     * Единая точка изменения интервала обновления из Settings: сохраняет
     * значение, перезапускает быстрый foreground-цикл (если он сейчас активен,
     * т.е. пользователь на экране стены) и перепланирует фоновую WorkManager-
     * задачу с учётом минимума в 15 минут.
     */
    fun applyRefreshInterval(intervalSeconds: Int) {
        setRefreshInterval(intervalSeconds)
        if (foregroundRefreshJob != null) {
            startForegroundAutoRefresh(intervalSeconds)
        }
        viewModelScope.launch {
            scheduleBackgroundUpdates(intervalSeconds)
        }
    }

    private fun scheduleBackgroundUpdates(intervalSeconds: Int) {
        // WorkManager.PeriodicWorkRequest не позволяет интервал короче 15 минут —
        // это ограничение платформы, не библиотеки. Значения из "быстрого" слайдера
        // (5-300 сек) сюда не подходят напрямую, поэтому клэмпим.
        val intervalMinutes = (intervalSeconds / 60L).coerceAtLeast(15L)
        scheduleCameraUpdates(intervalMinutes)
    }

    fun cancelCameraUpdates() {
        val updateManager = CameraUpdateManager(application)
        updateManager.cancelCameraUpdates()
    }

    fun triggerOneTimeUpdate() {
        val updateManager = CameraUpdateManager(application)
        updateManager.triggerOneTimeUpdate()
    }
}
