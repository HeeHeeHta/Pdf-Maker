package com.example.ui.pdfsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ImageFitOption
import com.example.domain.model.ImageQualityOption
import com.example.domain.model.MarginOption
import com.example.domain.model.OrientationOption
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfGenerationState
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onGenerationSuccess: () -> Unit
) {
    val pdfSettings by viewModel.pdfSettings.collectAsState()
    val generationState by viewModel.generationState.collectAsState()

    // React to success state and navigate
    LaunchedEffect(generationState) {
        if (generationState is PdfGenerationState.Success) {
            onGenerationSuccess()
        }
    }

    val isGenerating = generationState is PdfGenerationState.Generating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Conversion Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isGenerating,
                        modifier = Modifier.testTag("back_to_editor_btn")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (generationState is PdfGenerationState.Generating) {
                        val state = generationState as PdfGenerationState.Generating
                        val progress = state.currentPage.toFloat() / state.totalPages.toFloat()

                        Text(
                            text = "Generating PDF... ${state.currentPage} / ${state.totalPages}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Button(
                            onClick = { viewModel.generatePdf() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("generate_pdf_btn")
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert to PDF Now", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filename Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PDF Filename", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pdfSettings.fileName,
                        onValueChange = { newName ->
                            viewModel.updatePdfSettings(pdfSettings.copy(fileName = newName))
                        },
                        placeholder = { Text("PDF_2026-08-13_22-30-15.pdf") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_filename_input")
                    )
                }
            }

            // Page Size Option
            SettingsGroupCard(title = "Page Size") {
                FlowRowLayout {
                    PageSizeOption.values().forEach { option ->
                        FilterChip(
                            selected = pdfSettings.pageSize == option,
                            onClick = {
                                viewModel.updatePdfSettings(pdfSettings.copy(pageSize = option))
                            },
                            label = { Text(option.displayName.split(" ").first()) }
                        )
                    }
                }
            }

            // Orientation Option
            SettingsGroupCard(title = "Orientation") {
                FlowRowLayout {
                    OrientationOption.values().forEach { option ->
                        FilterChip(
                            selected = pdfSettings.orientation == option,
                            onClick = {
                                viewModel.updatePdfSettings(pdfSettings.copy(orientation = option))
                            },
                            label = { Text(option.displayName.split(" ").first()) }
                        )
                    }
                }
            }

            // Image Fit Option
            SettingsGroupCard(title = "Image Fit Mode") {
                FlowRowLayout {
                    ImageFitOption.values().forEach { option ->
                        FilterChip(
                            selected = pdfSettings.imageFit == option,
                            onClick = {
                                viewModel.updatePdfSettings(pdfSettings.copy(imageFit = option))
                            },
                            label = { Text(option.displayName.split(" ").first()) }
                        )
                    }
                }
            }

            // Margin Option
            SettingsGroupCard(title = "Margin") {
                FlowRowLayout {
                    MarginOption.values().forEach { option ->
                        FilterChip(
                            selected = pdfSettings.margin == option,
                            onClick = {
                                viewModel.updatePdfSettings(pdfSettings.copy(margin = option))
                            },
                            label = { Text(option.displayName.split(" ").first()) }
                        )
                    }
                }
            }

            // Image Quality Option
            SettingsGroupCard(title = "Image Quality") {
                FlowRowLayout {
                    ImageQualityOption.values().forEach { option ->
                        FilterChip(
                            selected = pdfSettings.imageQuality == option,
                            onClick = {
                                viewModel.updatePdfSettings(pdfSettings.copy(imageQuality = option))
                            },
                            label = { Text(option.name) }
                        )
                    }
                }
            }

            // Intelligent Compression Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Compression",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reduces PDF file size for fast sharing without sacrificing document readability.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = pdfSettings.enableIntelligentCompression,
                        onCheckedChange = { enabled ->
                            viewModel.updatePdfSettings(pdfSettings.copy(enableIntelligentCompression = enabled))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun FlowRowLayout(
    content: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
