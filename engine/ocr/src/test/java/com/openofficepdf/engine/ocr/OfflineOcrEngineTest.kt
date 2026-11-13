package com.openofficepdf.engine.ocr

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class OfflineOcrEngineTest {

    @Test
    fun testPdfContentStreamParsing_extractsText() {
        val simulatedPdfStream = """
            %PDF-1.4
            1 0 obj
            << /Length 120 >>
            stream
            BT
            /F1 14 Tf
            100 700 Td
            (OpenOfficePDF Offline OCR Test) Tj
            ET
            endstream
            endobj
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val blocks = PdfTextContentExtractor.extractTextFromPdfStream(
            ByteArrayInputStream(simulatedPdfStream),
            targetPageIndex = 0
        )

        assertNotNull(blocks)
        assertTrue("Should extract text blocks from PDF stream", blocks.isNotEmpty())
        assertEquals("OpenOfficePDF Offline OCR Test", blocks[0].text)
        assertEquals(blocks[0].text.length, blocks[0].characters.size)
        assertTrue(blocks[0].confidence >= 0.90f)
    }

    @Test
    fun testPdfContentStreamWithTJArray_extractsCombinedWords() {
        val simulatedPdfStream = """
            stream
            BT
            /F1 12 Tf
            50 650 Td
            [(Offline) 20 (Document) 10 (Analysis)] TJ
            ET
            endstream
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val blocks = PdfTextContentExtractor.extractTextFromPdfStream(
            ByteArrayInputStream(simulatedPdfStream),
            targetPageIndex = 0
        )

        assertNotNull(blocks)
        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks[0].text.contains("Offline") && blocks[0].text.contains("Document"))
    }
}
