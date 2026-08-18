package ru.wizand.camwall.data.repository

import kotlinx.coroutines.flow.first
import ru.wizand.camwall.data.local.database.CameraDao
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository

class CameraRepositoryImpl(
    private val cameraDao: CameraDao
) : ICameraRepository {

    override suspend fun getCameras(): Result<List<Camera>> {
        return try {
            val cameras = cameraDao.getAllCameras().first() ?: emptyList()
            Result.success(cameras)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCameraById(id: Int): Result<Camera?> {
        return try {
            Result.success(cameraDao.getCameraById(id.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    override suspend fun deleteCamera(id: Int): Result<Unit> {
        return try {
            cameraDao.deleteCameraById(id.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}