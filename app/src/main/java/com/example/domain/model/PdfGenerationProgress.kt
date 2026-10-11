package com.example.domain.model

sealed class PdfGenerationState {
    object Idle : PdfGenerationState()
    data class Generating(val currentPage: Int, val totalPages: Int) : PdfGenerationState()
    data class Success(
        val fileUri: String,
        val filePath: String,
        val fileName: String,
        val pageCount: Int,
        val fileSize: Long
    ) : PdfGenerationState()
    data class Error(val message: String) : PdfGenerationState()
}
