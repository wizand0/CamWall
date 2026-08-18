package ru.wizand.camwall.data.repository

import kotlinx.coroutines.flow.Flow
import ru.wizand.camwall.data.local.database.CameraDao
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository
import javax.inject.Inject

class CameraRepositoryImpl @Inject constructor(
    private val cameraDao: CameraDao
) : ICameraRepository {

    override fun getAllCameras(): Flow<List<Camera>> {
        return cameraDao.getAllCameras()
    }

    override suspend fun getCameraById(id: String): Camera? {
        return cameraDao.getCameraById(id)
    }

    override suspend fun insertCamera(camera: Camera) {
        cameraDao.insertCamera(camera)
    }

    override suspend fun updateCamera(camera: Camera) {
        cameraDao.updateCamera(camera)
    }

    override suspend fun deleteCamera(id: String) {
        cameraDao.deleteCameraById(id)
    }

    override suspend fun updateCameraFrame(
        cameraId: String,
        frameTimestamp: Long,
        error: String?
    ) {
        cameraDao.updateCameraFrame(
            cameraId = cameraId,
            frameTimestamp = if (error == null) frameTimestamp else null,
            error = error
        )
    }
}