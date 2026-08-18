package ru.wizand.camwall.domain.usecase

import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository

class AddCameraUseCase(
    private val repository: ICameraRepository
) {
    suspend operator fun invoke(camera: Camera): Result<Unit> {
        return repository.addCamera(camera)
    }
}