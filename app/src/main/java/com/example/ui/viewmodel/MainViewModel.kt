package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.RecentPdfEntity
import com.example.data.repository.RecentPdfRepository
import com.example.domain.model.FilterMode
import com.example.domain.model.PdfGenerationState
import com.example.domain.model.PdfPageItem
import com.example.domain.model.PdfSettings
import com.example.pdf.PdfGenerator
import com.example.pdf.PdfMerger
import com.example.pdf.PdfToImageExtractor
import com.example.storage.FileManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecentPdfRepository by lazy {
        val db = AppDatabase.getDatabase(application)
        RecentPdfRepository(db.recentPdfDao())
    }

    // Selected Images / Pages for PDF Generation
    private val _selectedPages = MutableStateFlow<List<PdfPageItem>>(emptyList())
    val selectedPages: StateFlow<List<PdfPageItem>> = _selectedPages.asStateFlow()

    // PDF Settings State
    private val _pdfSettings = MutableStateFlow(PdfSettings())
    val pdfSettings: StateFlow<PdfSettings> = _pdfSettings.asStateFlow()

    // PDF Generation Progress State
    private val _generationState = MutableStateFlow<PdfGenerationState>(PdfGenerationState.Idle)
    val generationState: StateFlow<PdfGenerationState> = _generationState.asStateFlow()

    // Recent PDFs State
    val recentPdfs: StateFlow<List<RecentPdfEntity>> = repository.recentPdfs
        .stateInScope(emptyList())

    // UI Toast / Message Events
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // PDF Tools States
    private val _mergePdfUris = MutableStateFlow<List<Uri>>(emptyList())
    val mergePdfUris: StateFlow<List<Uri>> = _mergePdfUris.asStateFlow()

    private val _pdfToolsProgress = MutableStateFlow<String?>(null)
    val pdfToolsProgress: StateFlow<String?> = _pdfToolsProgress.asStateFlow()

    init {
        viewModelScope.launch {
            recentPdfs.collectLatest { list ->
                repository.syncWithFileSystem(list)
            }
        }
    }

    // Helper for stateIn without complex boilerplate
    private fun <T> kotlinx.coroutines.flow.Flow<T>.stateInScope(initialValue: T): StateFlow<T> {
        val state = MutableStateFlow(initialValue)
        viewModelScope.launch {
            this@stateInScope.collect { state.value = it }
        }
        return state.asStateFlow()
    }

    // --- Page Management Actions ---

    fun addImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _selectedPages.value.toMutableList()
        val newItems = uris.map { PdfPageItem(uri = it) }
        current.addAll(newItems)
        _selectedPages.value = current
    }

    fun removePage(id: String) {
        _selectedPages.value = _selectedPages.value.filter { it.id != id }
    }

    fun rotatePage(id: String) {
        _selectedPages.value = _selectedPages.value.map { item ->
            if (item.id == id) {
                item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
            } else {
                item
            }
        }
    }

    fun applyFilterToPage(id: String, filterMode: FilterMode) {
        _selectedPages.value = _selectedPages.value.map { item ->
            if (item.id == id) {
                item.copy(filterMode = filterMode)
            } else {
                item
            }
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val list = _selectedPages.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _selectedPages.value = list
        }
    }

    fun clearSelectedPages() {
        _selectedPages.value = emptyList()
        _generationState.value = PdfGenerationState.Idle
    }

    // --- PDF Settings Actions ---

    fun updatePdfSettings(newSettings: PdfSettings) {
        _pdfSettings.value = newSettings
    }

    // --- PDF Generation Action ---

    fun generatePdf() {
        val pages = _selectedPages.value
        if (pages.isEmpty()) {
            emitUserMessage("Please select at least one image.")
            return
        }

        _generationState.value = PdfGenerationState.Generating(0, pages.size)

        viewModelScope.launch {
            val context = getApplication<Application>()
            val outputDir = FileManager.getAppPdfDir(context)

            PdfGenerator.generatePdf(
                context = context,
                pageItems = pages,
                settings = _pdfSettings.value,
                outputDir = outputDir,
                onProgress = { current, total ->
                    _generationState.value = PdfGenerationState.Generating(current, total)
                }
            ).onSuccess { generatedFile ->
                val fileSize = generatedFile.length()
                val pageCount = pages.size
                val fileName = generatedFile.name

                repository.addRecentPdf(
                    filePath = generatedFile.absolutePath,
                    fileName = fileName,
                    pageCount = pageCount,
                    fileSize = fileSize
                )

                _generationState.value = PdfGenerationState.Success(
                    fileUri = generatedFile.absolutePath,
                    filePath = generatedFile.absolutePath,
                    fileName = fileName,
                    pageCount = pageCount,
                    fileSize = fileSize
                )
            }.onFailure { exception ->
                _generationState.value = PdfGenerationState.Error(
                    exception.localizedMessage ?: "Failed to generate PDF."
                )
            }
        }
    }

    // --- Recent Files Actions ---

    fun deleteRecentPdf(entity: RecentPdfEntity) {
        viewModelScope.launch {
            repository.deleteRecentPdf(entity.id)
            val file = File(entity.filePath)
            if (file.exists()) {
                file.delete()
            }
            emitUserMessage("PDF deleted.")
        }
    }

    // --- PDF Tools: Merge & Extract ---

    fun setMergePdfUris(uris: List<Uri>) {
        _mergePdfUris.value = uris
    }

    fun mergePdfs(outputFileName: String) {
        val uris = _mergePdfUris.value
        if (uris.size < 2) {
            emitUserMessage("Please select at least 2 PDF files to merge.")
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val outputDir = FileManager.getAppPdfDir(context)
            val file = File(outputDir, if (outputFileName.endsWith(".pdf")) outputFileName else "$outputFileName.pdf")

            _pdfToolsProgress.value = "Merging PDFs..."

            PdfMerger.mergePdfs(
                context = context,
                pdfUris = uris,
                outputFile = file,
                onProgress = { current, total ->
                    _pdfToolsProgress.value = "Processing PDF $current of $total..."
                }
            ).onSuccess { mergedFile ->
                repository.addRecentPdf(
                    filePath = mergedFile.absolutePath,
                    fileName = mergedFile.name,
                    pageCount = uris.size, // Estimated
                    fileSize = mergedFile.length()
                )
                _pdfToolsProgress.value = null
                emitUserMessage("PDFs merged successfully: ${mergedFile.name}")
            }.onFailure {
                _pdfToolsProgress.value = null
                emitUserMessage("Failed to merge PDFs: ${it.localizedMessage}")
            }
        }
    }

    fun extractPdfToImages(pdfUri: Uri, pageIndices: List<Int>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _pdfToolsProgress.value = "Extracting images..."

            PdfToImageExtractor.extractPagesToImages(
                context = context,
                pdfUri = pdfUri,
                pageIndices = pageIndices,
                onProgress = { current, total ->
                    _pdfToolsProgress.value = "Extracting page $current of $total..."
                }
            ).onSuccess { uris ->
                _pdfToolsProgress.value = null
                emitUserMessage("Successfully saved ${uris.size} images to Pictures!")
            }.onFailure {
                _pdfToolsProgress.value = null
                emitUserMessage("Extraction failed: ${it.localizedMessage}")
            }
        }
    }

    fun emitUserMessage(msg: String) {
        viewModelScope.launch {
            _userMessage.emit(msg)
        }
    }
}
