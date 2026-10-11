package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfMerger {

    suspend fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>,
        outputFile: File,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pdfUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No PDF files selected for merging"))
        }

        val mergedPdfDocument = PdfDocument()
        var globalPageNumber = 1

        try {
            val totalPdfCount = pdfUris.size
            for (i in pdfUris.indices) {
                val uri = pdfUris[i]
                onProgress(i + 1, totalPdfCount)

                val pfd: ParcelFileDescriptor? = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (e: Exception) {
                    null
                }

                if (pfd != null) {
                    try {
                        val renderer = PdfRenderer(pfd)
                        val pageCount = renderer.pageCount

                        for (p in 0 until pageCount) {
                            val rendererPage = renderer.openPage(p)
                            val pageWidth = rendererPage.width
                            val pageHeight = rendererPage.height

                            val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                            rendererPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, globalPageNumber).create()
                            val docPage = mergedPdfDocument.startPage(pageInfo)
                            val canvas: Canvas = docPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)

                            mergedPdfDocument.finishPage(docPage)

                            bitmap.recycle()
                            rendererPage.close()
                            globalPageNumber++
                        }

                        renderer.close()
                    } finally {
                        pfd.close()
                    }
                }
            }

            FileOutputStream(outputFile).use { outStream ->
                mergedPdfDocument.writeTo(outStream)
            }
            mergedPdfDocument.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                mergedPdfDocument.close()
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}
