package com.example.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfToImageExtractor {

    data class PdfPagePreview(
        val pageIndex: Int,
        val width: Int,
        val height: Int
    )

    suspend fun getPdfPageCount(context: Context, pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    return@withContext renderer.pageCount
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun extractPagesToImages(
        context: Context,
        pdfUri: Uri,
        pageIndices: List<Int>, // 0-indexed
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        val savedUris = mutableListOf<Uri>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext Result.failure(Exception("Cannot open PDF file"))

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val total = pageIndices.size
                    val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
                    val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"

                    for (i in pageIndices.indices) {
                        val pageIdx = pageIndices[i]
                        if (pageIdx < 0 || pageIdx >= renderer.pageCount) continue

                        onProgress(i + 1, total)

                        val page = renderer.openPage(pageIdx)
                        // Scale up bitmap 2x for high resolution output
                        val scale = 2
                        val bitmap = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val fileName = "PDF_Page_${pageIdx + 1}_${System.currentTimeMillis()}.$ext"

                        val imageUri = saveBitmapToMediaStore(
                            context = context,
                            bitmap = bitmap,
                            fileName = fileName,
                            mimeType = mimeType,
                            format = format
                        )

                        bitmap.recycle()

                        if (imageUri != null) {
                            savedUris.add(imageUri)
                        }
                    }
                }
            }

            Result.success(savedUris)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        mimeType: String,
        format: Bitmap.CompressFormat
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PDF_Extracted")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageUri = resolver.insert(collection, contentValues) ?: return null

        try {
            resolver.openOutputStream(imageUri)?.use { out ->
                bitmap.compress(format, 90, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            return imageUri
        } catch (e: Exception) {
            resolver.delete(imageUri, null, null)
            return null
        }
    }
}
