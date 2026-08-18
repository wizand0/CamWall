package ru.wizand.camwall.domain.repository

import ru.wizand.camwall.domain.model.Camera

interface ICameraRepository {
    suspend fun getCameras(): Result<List<Camera>>
    suspend fun getCameraById(id: Int): Result<Camera?>
    suspend fun addCamera(camera: Camera): Result<Unit>
    suspend fun updateCamera(camera: Camera): Result<Unit>
    suspend fun deleteCamera(id: Int): Result<Unit>
}