package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.domain.model.ImageFitOption
import com.example.domain.model.OrientationOption
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfPageItem
import com.example.domain.model.PdfSettings
import com.example.image.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    /**
     * Generates a PDF file sequentially from a list of PdfPageItems.
     * Guarantees memory efficiency by processing one image bitmap at a time.
     */
    suspend fun generatePdf(
        context: Context,
        pageItems: List<PdfPageItem>,
        settings: PdfSettings,
        outputDir: File,
        onProgress: suspend (currentPage: Int, totalPages: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pageItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No images selected to generate PDF."))
        }

        val pdfDocument = PdfDocument()
        val totalPages = pageItems.size

        // Sanitize or auto-generate filename
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val rawName = settings.fileName.trim().ifEmpty { "PDF_$timestamp.pdf" }
        val sanitizedFileName = if (rawName.endsWith(".pdf", ignoreCase = true)) {
            sanitizeFileName(rawName)
        } else {
            "${sanitizeFileName(rawName)}.pdf"
        }

        val outputFile = File(outputDir, sanitizedFileName)

        try {
            for (i in pageItems.indices) {
                val pageItem = pageItems[i]
                onProgress(i + 1, totalPages)

                // 1. Load memory-safe bitmap for current page
                val bitmap = ImageProcessor.loadAndProcessBitmap(
                    context = context,
                    pageItem = pageItem,
                    maxTargetDimension = settings.imageQuality.maxDimension
                ) ?: continue

                // 2. Determine Page Dimensions & Orientation
                val bitmapWidth = bitmap.width
                val bitmapHeight = bitmap.height

                val (pageWidth, pageHeight) = calculatePageDimensions(
                    settings = settings,
                    bitmapWidth = bitmapWidth,
                    bitmapHeight = bitmapHeight
                )

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Background fill white
                canvas.drawColor(Color.WHITE)

                // 3. Draw Bitmap into canvas according to Margins & ImageFit
                drawBitmapToCanvas(
                    canvas = canvas,
                    bitmap = bitmap,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    settings = settings
                )

                pdfDocument.finishPage(page)

                // 4. CRITICAL: Recycle bitmap immediately after page completion
                bitmap.recycle()
            }

            // Save PDF document stream to output file
            FileOutputStream(outputFile).use { outStream ->
                pdfDocument.writeTo(outStream)
            }

            pdfDocument.close()
            Result.success(outputFile)

        } catch (e: Exception) {
            e.printStackTrace()
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    private fun calculatePageDimensions(
        settings: PdfSettings,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Pair<Int, Int> {
        val baseSize = settings.pageSize

        if (baseSize == PageSizeOption.ORIGINAL_IMAGE) {
            val margin = settings.margin.marginDp * 2
            return Pair(bitmapWidth + margin, bitmapHeight + margin)
        }

        var width = baseSize.widthPoints
        var height = baseSize.heightPoints

        when (settings.orientation) {
            OrientationOption.PORTRAIT -> {
                if (width > height) {
                    val temp = width
                    width = height
                    height = temp
                }
            }
            OrientationOption.LANDSCAPE -> {
                if (width < height) {
                    val temp = width
                    width = height
                    height = temp
                }
            }
            OrientationOption.AUTO -> {
                val isImageLandscape = bitmapWidth > bitmapHeight
                if (isImageLandscape && width < height) {
                    val temp = width
                    width = height
                    height = temp
                } else if (!isImageLandscape && width > height) {
                    val temp = width
                    width = height
                    height = temp
                }
            }
        }

        return Pair(width, height)
    }

    private fun drawBitmapToCanvas(
        canvas: Canvas,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        settings: PdfSettings
    ) {
        val margin = settings.margin.marginDp.toFloat()
        val availWidth = (pageWidth - (margin * 2)).coerceAtLeast(1f)
        val availHeight = (pageHeight - (margin * 2)).coerceAtLeast(1f)

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        val destRect: RectF = when (settings.imageFit) {
            ImageFitOption.FIT -> {
                val scale = minOf(availWidth / bmpW, availHeight / bmpH)
                val drawW = bmpW * scale
                val drawH = bmpH * scale
                val left = margin + (availWidth - drawW) / 2f
                val top = margin + (availHeight - drawH) / 2f
                RectF(left, top, left + drawW, top + drawH)
            }
            ImageFitOption.FILL -> {
                val scale = maxOf(availWidth / bmpW, availHeight / bmpH)
                val drawW = bmpW * scale
                val drawH = bmpH * scale
                val left = margin + (availWidth - drawW) / 2f
                val top = margin + (availHeight - drawH) / 2f
                RectF(left, top, left + drawW, top + drawH)
            }
            ImageFitOption.ORIGINAL -> {
                val drawW = bmpW.coerceAtMost(availWidth)
                val drawH = bmpH.coerceAtMost(availHeight)
                val left = margin + (availWidth - drawW) / 2f
                val top = margin + (availHeight - drawH) / 2f
                RectF(left, top, left + drawW, top + drawH)
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    }

    fun sanitizeFileName(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
