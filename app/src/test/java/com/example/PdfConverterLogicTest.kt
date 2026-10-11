package com.example

import com.example.domain.model.MarginOption
import com.example.domain.model.OrientationOption
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfSettings
import com.example.pdf.PdfGenerator
import com.example.storage.FileManager
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfConverterLogicTest {

    @Test
    fun `test sanitize filename removes invalid path characters`() {
        val unsafeName = "My/Doc*Name?.pdf"
        val clean = PdfGenerator.sanitizeFileName(unsafeName)
        assertEquals("My_Doc_Name_.pdf", clean)
    }

    @Test
    fun `test default pdf settings defaults`() {
        val settings = PdfSettings()
        assertEquals(PageSizeOption.A4, settings.pageSize)
        assertEquals(OrientationOption.AUTO, settings.orientation)
        assertEquals(MarginOption.SMALL, settings.margin)
    }

    @Test
    fun `test file size formatting`() {
        val sizeInBytes = 2 * 1024 * 1024L
        val formatted = FileManager.formatFileSize(sizeInBytes)
        assertEquals("2 MB", formatted)
    }
}
