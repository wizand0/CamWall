package ru.wizand.camwall.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.usecase.GetCamerasUseCase

class CameraWallViewModel(
    private val getCamerasUseCase: GetCamerasUseCase
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<Camera>>(emptyList())
    val cameras: StateFlow<List<Camera>> = _cameras.asStateFlow()

    init {
        loadCameras()
    }

    private fun loadCameras() {
        viewModelScope.launch {
            val result = getCamerasUseCase.invoke()
            if(result.isSuccess) {
                _cameras.value = result.getOrNull() ?: emptyList()
            }
        }
    }
}