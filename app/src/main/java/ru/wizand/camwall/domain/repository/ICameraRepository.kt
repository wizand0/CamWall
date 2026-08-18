package ru.wizand.camwall.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.wizand.camwall.domain.model.Camera

interface ICameraRepository {
    fun getAllCameras(): Flow<List<Camera>>
    suspend fun getCameraById(id: String): Camera?
    suspend fun insertCamera(camera: Camera)
    suspend fun updateCamera(camera: Camera)
    suspend fun deleteCamera(id: String)
    suspend fun updateCameraFrame(
        cameraId: String,
        frameTimestamp: Long,
        error: String? = null
    )
}