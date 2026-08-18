package ru.wizand.camwall.domain.usecase

import ru.wizand.camwall.domain.repository.ICameraRepository
import javax.inject.Inject

class DeleteCameraUseCase @Inject constructor(
    private val repository: ICameraRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteCamera(id)
    }
}