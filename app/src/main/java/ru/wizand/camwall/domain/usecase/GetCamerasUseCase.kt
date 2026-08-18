package ru.wizand.camwall.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository

class GetCamerasUseCase(
    private val repository: ICameraRepository
) {
    suspend operator fun invoke(): Result<List<Camera>> {
        return repository.getCameras()
    }
}