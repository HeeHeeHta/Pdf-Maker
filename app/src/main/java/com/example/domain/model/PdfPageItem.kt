package com.example.domain.model

import android.net.Uri

data class PdfPageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val rotationDegrees: Int = 0,
    val cropTopPercent: Float = 0f,
    val cropBottomPercent: Float = 0f,
    val cropLeftPercent: Float = 0f,
    val cropRightPercent: Float = 0f,
    val filterMode: FilterMode = FilterMode.ORIGINAL
)

enum class FilterMode {
    ORIGINAL,
    PAPER_FIT,
    GRAYSCALE,
    BLACK_AND_WHITE,
    HIGH_CONTRAST
}
