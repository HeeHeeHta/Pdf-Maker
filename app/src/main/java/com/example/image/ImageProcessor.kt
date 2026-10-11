package com.example.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.example.domain.model.FilterMode
import com.example.domain.model.PdfPageItem
import java.io.InputStream

object ImageProcessor {

    /**
     * Loads a processed, memory-safe Bitmap for a single page item.
     * Reclaims memory safely by sampling down large images before decoding.
     */
    fun loadAndProcessBitmap(
        context: Context,
        pageItem: PdfPageItem,
        maxTargetDimension: Int = 2400
    ): Bitmap? {
        val uri = pageItem.uri
        val contentResolver = context.contentResolver

        // 1. Read image bounds first without allocating full bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // 2. Calculate safe inSampleSize
        var sampleSize = 1
        var tempW = origWidth
        var tempH = origHeight

        while (tempW > maxTargetDimension || tempH > maxTargetDimension) {
            tempW /= 2
            tempH /= 2
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        // 3. Decode scaled bitmap
        var bitmap: Bitmap? = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null

        // 4. Read EXIF Orientation
        val exifRotation = getExifRotationDegrees(context, uri)

        // 5. Total rotation = EXIF rotation + user rotation
        val totalRotation = (exifRotation + pageItem.rotationDegrees) % 360

        // Apply cropping if requested
        if (pageItem.cropTopPercent > 0 || pageItem.cropBottomPercent > 0 ||
            pageItem.cropLeftPercent > 0 || pageItem.cropRightPercent > 0) {
            val curW = bitmap!!.width
            val curH = bitmap.height

            val left = (curW * pageItem.cropLeftPercent).toInt().coerceIn(0, curW - 1)
            val top = (curH * pageItem.cropTopPercent).toInt().coerceIn(0, curH - 1)
            val right = (curW * (1f - pageItem.cropRightPercent)).toInt().coerceIn(left + 1, curW)
            val bottom = (curH * (1f - pageItem.cropBottomPercent)).toInt().coerceIn(top + 1, curH)

            val cropW = right - left
            val cropH = bottom - top

            if (cropW > 0 && cropH > 0) {
                val cropped = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
                if (cropped != bitmap) {
                    bitmap.recycle()
                    bitmap = cropped
                }
            }
        }

        // Apply matrix transformation (rotation)
        if (totalRotation != 0) {
            val matrix = Matrix().apply { postRotate(totalRotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        // Apply filter mode (Grayscale, High Contrast, etc.)
        if (pageItem.filterMode != FilterMode.ORIGINAL && bitmap != null) {
            val filtered = applyFilter(bitmap, pageItem.filterMode)
            if (filtered != bitmap) {
                bitmap.recycle()
                bitmap = filtered
            }
        }

        return bitmap
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun applyFilter(src: Bitmap, filterMode: FilterMode): Bitmap {
        val config = src.config ?: Bitmap.Config.ARGB_8888
        val dest = Bitmap.createBitmap(src.width, src.height, config)
        val canvas = Canvas(dest)
        val paint = Paint()

        val cm = ColorMatrix()
        when (filterMode) {
            FilterMode.PAPER_FIT -> return applyPaperFitFilter(src)
            FilterMode.GRAYSCALE -> cm.setSaturation(0f)
            FilterMode.BLACK_AND_WHITE -> {
                cm.setSaturation(0f)
                // Increase contrast drastically
                val contrast = 2.0f
                val scale = contrast
                val translate = (-0.5f * scale + 0.5f) * 255f
                val contrastMatrix = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(contrastMatrix))
            }
            FilterMode.HIGH_CONTRAST -> {
                val contrast = 1.4f
                val scale = contrast
                val translate = (-0.5f * scale + 0.5f) * 255f
                val contrastMatrix = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(contrastMatrix)
            }
            FilterMode.ORIGINAL -> return src
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    enum class DocumentScanFilter {
        PAPER_FIT,
        BLACK_AND_WHITE,
        ORIGINAL
    }

    /**
     * Auto-crops outer document frame boundaries and applies Paper Fit enhancement
     * (brightens paper background to pure white, sharpens text, and fits to standard paper proportion).
     */
    fun processDocumentScan(
        context: Context,
        inputUri: Uri,
        filterMode: DocumentScanFilter = DocumentScanFilter.PAPER_FIT,
        cropMarginPercent: Float = 0.05f
    ): Uri? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(inputUri) ?: return null
            var srcBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            // Read EXIF orientation
            val exifRotation = getExifRotationDegrees(context, inputUri)
            if (exifRotation != 0) {
                val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
                val rotated = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.width, srcBitmap.height, matrix, true)
                if (rotated != srcBitmap) {
                    srcBitmap.recycle()
                    srcBitmap = rotated
                }
            }

            // 1. Detect & Crop Document Borders
            val croppedBitmap = detectAndCropDocument(srcBitmap, cropMarginPercent)

            // 2. Apply Paper Whitening / Enhancement Filter
            val enhancedBitmap = when (filterMode) {
                DocumentScanFilter.PAPER_FIT -> applyPaperFitFilter(croppedBitmap)
                DocumentScanFilter.BLACK_AND_WHITE -> applyFilter(croppedBitmap, FilterMode.BLACK_AND_WHITE)
                DocumentScanFilter.ORIGINAL -> croppedBitmap
            }

            // 3. Fit Enhanced Document onto Clean Standard A4 Paper Canvas (Aspect Ratio 1:1.414)
            val finalBitmap = if (filterMode == DocumentScanFilter.PAPER_FIT) {
                fitToPaperCanvas(enhancedBitmap)
            } else {
                enhancedBitmap
            }

            // 4. Save processed document image to cache file
            val outputFile = java.io.File(context.cacheDir, "doc_scan_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(outputFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            // Clean up temporary bitmaps
            if (croppedBitmap != srcBitmap && croppedBitmap != finalBitmap) croppedBitmap.recycle()
            if (enhancedBitmap != croppedBitmap && enhancedBitmap != finalBitmap) enhancedBitmap.recycle()
            if (finalBitmap != srcBitmap) finalBitmap.recycle()
            srcBitmap.recycle()

            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            inputUri
        }
    }

    /**
     * Smart Auto-Crop: Detects paper bounds or trims outer border margins.
     */
    private fun detectAndCropDocument(src: Bitmap, defaultMarginPercent: Float = 0.05f): Bitmap {
        val w = src.width
        val h = src.height

        // Downsample for fast scanning
        val sampleW = 100
        val sampleH = 100
        val sampleBitmap = Bitmap.createScaledBitmap(src, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        sampleBitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        sampleBitmap.recycle()

        // Calculate average edge brightness to find background vs paper
        var edgeLumSum = 0L
        var edgeCount = 0
        for (x in 0 until sampleW) {
            val topColor = pixels[x]
            val bottomColor = pixels[(sampleH - 1) * sampleW + x]
            edgeLumSum += getLuminance(topColor) + getLuminance(bottomColor)
            edgeCount += 2
        }
        val avgEdgeLum = if (edgeCount > 0) edgeLumSum / edgeCount else 80

        // Find paper bounds where luminance exceeds edge luminance by threshold
        var minX = sampleW
        var maxX = 0
        var minY = sampleH
        var maxY = 0

        val lumThreshold = (avgEdgeLum + 25).coerceIn(90, 180)

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val lum = getLuminance(pixels[y * sampleW + x])
                if (lum >= lumThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val cropLeft: Int
        val cropTop: Int
        val cropRight: Int
        val cropBottom: Int

        // Verify if a valid bounding box was detected
        if (maxX > minX + 20 && maxY > minY + 20) {
            // Map back to original dimensions with safety padding
            cropLeft = ((minX.toFloat() / sampleW) * w).toInt().coerceIn(0, w / 4)
            cropTop = ((minY.toFloat() / sampleH) * h).toInt().coerceIn(0, h / 4)
            cropRight = ((maxX.toFloat() / sampleW) * w).toInt().coerceIn(w * 3 / 4, w)
            cropBottom = ((maxY.toFloat() / sampleH) * h).toInt().coerceIn(h * 3 / 4, h)
        } else {
            // Fallback: Default margin crop
            cropLeft = (w * defaultMarginPercent).toInt().coerceIn(0, w / 4)
            cropTop = (h * defaultMarginPercent).toInt().coerceIn(0, h / 4)
            cropRight = (w * (1 - defaultMarginPercent)).toInt().coerceIn(w * 3 / 4, w)
            cropBottom = (h * (1 - defaultMarginPercent)).toInt().coerceIn(h * 3 / 4, h)
        }

        val cropW = (cropRight - cropLeft).coerceAtLeast(100)
        val cropH = (cropBottom - cropTop).coerceAtLeast(100)

        return Bitmap.createBitmap(src, cropLeft, cropTop, cropW, cropH)
    }

    private fun getLuminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }

    /**
     * Converts camera shadow/grey paper backgrounds to pure white while making text crisp and legible.
     */
    private fun applyPaperFitFilter(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Determine dynamic paper threshold from sample pixels
        var sumLum = 0L
        var count = 0
        val step = (w * h / 800).coerceAtLeast(1)
        for (i in 0 until pixels.size step step) {
            sumLum += getLuminance(pixels[i])
            count++
        }
        val avgLum = if (count > 0) sumLum / count else 130
        val paperThreshold = (avgLum * 0.82f).coerceIn(90f, 160f)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            if (lum >= paperThreshold) {
                // Paper Background -> Stretch aggressively to pure white (255)
                val t = ((lum - paperThreshold) / (255f - paperThreshold)).coerceIn(0f, 1f)
                val boost = (0.70f + t * 0.30f)
                val newR = (r + (255 - r) * boost).toInt().coerceIn(0, 255)
                val newG = (g + (255 - g) * boost).toInt().coerceIn(0, 255)
                val newB = (b + (255 - b) * boost).toInt().coerceIn(0, 255)

                pixels[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            } else {
                // Text / Ink -> Sharpen contrast
                val factor = 0.82f
                val newR = (r * factor).toInt().coerceIn(0, 255)
                val newG = (g * factor).toInt().coerceIn(0, 255)
                val newB = (b * factor).toInt().coerceIn(0, 255)

                pixels[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }

        val dest = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dest.setPixels(pixels, 0, w, 0, 0, w, h)
        return dest
    }

    /**
     * Fits the document onto a standard clean A4 paper canvas (aspect ratio 1 : 1.414).
     */
    private fun fitToPaperCanvas(src: Bitmap, targetAspectRatio: Float = 1f / 1.414f): Bitmap {
        val srcW = src.width
        val srcH = src.height
        val srcRatio = srcW.toFloat() / srcH.toFloat()

        val targetW: Int
        val targetH: Int

        if (srcRatio > targetAspectRatio) {
            targetW = srcW
            targetH = (srcW / targetAspectRatio).toInt()
        } else {
            targetH = srcH
            targetW = (srcH * targetAspectRatio).toInt()
        }

        val paperBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paperBitmap)
        canvas.drawColor(android.graphics.Color.WHITE) // Pure White Standard Paper Canvas

        val left = (targetW - srcW) / 2f
        val top = (targetH - srcH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, left, top, paint)

        return paperBitmap
    }
}
