package ru.wizand.camwall.domain.usecase

import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository
import javax.inject.Inject

class UpdateCameraUseCase @Inject constructor(
    private val repository: ICameraRepository
) {
    suspend operator fun invoke(camera: Camera) {
        repository.updateCamera(camera)
    }
}