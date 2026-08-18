package ru.wizand.camwall.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.repository.ICameraRepository
import javax.inject.Inject

class GetCamerasUseCase @Inject constructor(
    private val repository: ICameraRepository
) {
    operator fun invoke(): Flow<List<Camera>> {
        return repository.getAllCameras()
    }
}