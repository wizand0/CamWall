package ru.wizand.camwall.viewmodels

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository
import ru.wizand.camwall.domain.usecase.AddCameraUseCase
import ru.wizand.camwall.domain.usecase.DeleteCameraUseCase
import ru.wizand.camwall.domain.usecase.GetCamerasUseCase
import ru.wizand.camwall.domain.usecase.UpdateCameraUseCase
import ru.wizand.camwall.rtsp.RtspFrameCapture
import ru.wizand.camwall.manager.CameraUpdateManager

// Extension property to access DataStore
private val Context.dataStore by preferencesDataStore(name = "settings")

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

        private val REFRESH_INTERVAL_KEY = intPreferencesKey("refresh_interval")
        private val NIGHT_MODE_ENABLED_KEY = booleanPreferencesKey("night_mode_enabled")
        private val MAX_RETRIES_KEY = intPreferencesKey("max_retries")
    }

    init {
        loadCameras()
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
                val camera = Camera(name = name, rtspUrl = rtspUrl)
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
            _isLoading.value = true
            try {
                val cameras = getCamerasUseCase().getOrDefault(emptyList())
                cameras.forEach { camera ->
                    val updatedCamera = refreshCameraInternal(camera)
                    updateCameraUseCase(updatedCamera)
                }
                loadCameras() // Refresh the list
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing all cameras", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Пытается получить новый кадр для камеры.
     * При успехе обновляет lastSuccessfulFrameAt и сбрасывает ошибки;
     * при неудаче — lastAttemptAt, lastError и consecutiveErrors (старый кадр не трогается, ТЗ §9).
     */
    private suspend fun refreshCameraInternal(camera: Camera): Camera {
        return try {
            val result = rtspFrameCapture.captureFrame(camera.id, camera.rtspUrl)
            if (result.isSuccess) {
                camera.copy(
                    lastSuccessfulFrameAt = System.currentTimeMillis(),
                    lastAttemptAt = System.currentTimeMillis(),
                    lastError = null,
                    consecutiveErrors = 0
                )
            } else {
                camera.copy(
                    lastAttemptAt = System.currentTimeMillis(),
                    lastError = result.exceptionOrNull()?.message ?: "Unknown error",
                    consecutiveErrors = camera.consecutiveErrors + 1
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing camera ${camera.name}", e)
            camera.copy(
                lastAttemptAt = System.currentTimeMillis(),
                lastError = e.message,
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
        val preferences = application.dataStore.data.first()
        return preferences[REFRESH_INTERVAL_KEY] ?: DEFAULT_REFRESH_INTERVAL
    }

    fun setRefreshInterval(interval: Int) {
        viewModelScope.launch {
            application.dataStore.edit { preferences ->
                preferences[REFRESH_INTERVAL_KEY] = interval
            }
        }
    }

    suspend fun isNightModeEnabled(): Boolean {
        val preferences = application.dataStore.data.first()
        return preferences[NIGHT_MODE_ENABLED_KEY] ?: false
    }

    fun setNightModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            application.dataStore.edit { preferences ->
                preferences[NIGHT_MODE_ENABLED_KEY] = enabled
            }
        }
    }

    suspend fun getMaxRetries(): Int {
        val preferences = application.dataStore.data.first()
        return preferences[MAX_RETRIES_KEY] ?: DEFAULT_MAX_RETRIES
    }

    fun setMaxRetries(retries: Int) {
        viewModelScope.launch {
            application.dataStore.edit { preferences ->
                preferences[MAX_RETRIES_KEY] = retries
            }
        }
    }

    // Flow for a specific camera by ID
    fun getCameraById(id: String) = cameraRepository.getCameraById(id)

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

    fun cancelCameraUpdates() {
        val updateManager = CameraUpdateManager(application)
        updateManager.cancelCameraUpdates()
    }

    fun triggerOneTimeUpdate() {
        val updateManager = CameraUpdateManager(application)
        updateManager.triggerOneTimeUpdate()
    }
}
