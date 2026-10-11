package com.example.domain.model

enum class PageSizeOption(val displayName: String, val widthPoints: Int, val heightPoints: Int) {
    A4("A4 (595 x 842 pt)", 595, 842),
    A5("A5 (420 x 595 pt)", 420, 595),
    LETTER("Letter (612 x 792 pt)", 612, 792),
    ORIGINAL_IMAGE("Original Image Size", 0, 0)
}

enum class OrientationOption(val displayName: String) {
    AUTO("Auto (Based on image aspect ratio)"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ImageFitOption(val displayName: String) {
    FIT("Fit (Maintain aspect ratio, leave margins)"),
    FILL("Fill (Crop to fill page)"),
    ORIGINAL("Center Original Size")
}

enum class MarginOption(val displayName: String, val marginDp: Int) {
    NONE("None (0 pt)", 0),
    SMALL("Small (12 pt)", 12),
    MEDIUM("Medium (24 pt)", 24),
    LARGE("Large (36 pt)", 36)
}

enum class ImageQualityOption(val displayName: String, val maxDimension: Int, val compressionQuality: Int) {
    LOW("Low (Fast & Compact - 1000px, 60%)", 1000, 60),
    MEDIUM("Medium (Balanced - 1600px, 75%)", 1600, 75),
    HIGH("High (Print Quality - 2400px, 90%)", 2400, 90)
}

data class PdfSettings(
    val fileName: String = "",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: OrientationOption = OrientationOption.AUTO,
    val imageFit: ImageFitOption = ImageFitOption.FIT,
    val margin: MarginOption = MarginOption.SMALL,
    val imageQuality: ImageQualityOption = ImageQualityOption.HIGH,
    val enableIntelligentCompression: Boolean = true
)
