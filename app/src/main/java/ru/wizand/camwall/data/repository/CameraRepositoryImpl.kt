package ru.wizand.camwall.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ru.wizand.camwall.data.local.database.CameraDao
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository
import ru.wizand.camwall.security.RtspUrlCryptoStore

class CameraRepositoryImpl(
    private val cameraDao: CameraDao,
    private val cryptoStore: RtspUrlCryptoStore
) : ICameraRepository {

    override suspend fun getCameras(): Result<List<Camera>> {
        return try {
            val cameras = cameraDao.getAllCameras().first()
            Result.success(cameras)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCameraById(id: String): Flow<Camera?> {
        return cameraDao.getCameraById(id)
    }

    override suspend fun addCamera(camera: Camera): Result<Unit> {
        return try {
            cameraDao.insertCamera(camera)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateCamera(camera: Camera): Result<Unit> {
        return try {
            cameraDao.updateCamera(camera)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCamera(id: String): Result<Unit> {
        return try {
            cameraDao.deleteCameraById(id)
            removeRtspUrl(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getRtspUrl(cameraId: String): String? =
        cryptoStore.getUrl(cameraId)

    override fun saveRtspUrl(cameraId: String, url: String) {
        cryptoStore.storeUrl(cameraId, url)
    }

    override fun removeRtspUrl(cameraId: String) {
        cryptoStore.removeUrl(cameraId)
    }
}
