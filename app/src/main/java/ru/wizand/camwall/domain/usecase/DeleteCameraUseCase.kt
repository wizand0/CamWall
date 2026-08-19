package ru.wizand.camwall.domain.usecase

import ru.wizand.camwall.domain.repository.ICameraRepository

class DeleteCameraUseCase(
    private val repository: ICameraRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.deleteCamera(id)
    }
}