package ru.wizand.camwall.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.wizand.camwall.domain.model.Camera

interface ICameraRepository {
    suspend fun getCameras(): Result<List<Camera>>
    fun getCameraById(id: String): Flow<Camera?>
    suspend fun addCamera(camera: Camera): Result<Unit>
    suspend fun updateCamera(camera: Camera): Result<Unit>
    suspend fun deleteCamera(id: String): Result<Unit>
}