package ru.wizand.camwall.domain.model

import android.graphics.Bitmap
import java.io.File

data class Frame(
    val bitmap: Bitmap? = null,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0
)