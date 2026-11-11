package com.openofficepdf.engine.converter

import com.openofficepdf.core.model.DocumentFormat
import org.junit.Assert.*
import org.junit.Test

class ConversionQualityAdvisorTest {

    @Test
    fun testDocxToPdf_isLossless() {
        val assessment = ConversionQualityAdvisor.assessConversion(
            sourceFormat = DocumentFormat.DOCX,
            targetFormat = DocumentFormat.PDF
        )

        assertEquals(ConversionTier.LOSSLESS, assessment.tier)
        assertTrue(assessment.fidelityPercentageEstimate >= 98)
        assertFalse(assessment.isOcrRecommended)
    }

    @Test
    fun testPdfToDocx_isLayoutRebuild() {
        val assessment = ConversionQualityAdvisor.assessConversion(
            sourceFormat = DocumentFormat.PDF,
            targetFormat = DocumentFormat.DOCX,
            isScannedPdfOrImage = false
        )

        assertEquals(ConversionTier.LAYOUT_REBUILD, assessment.tier)
        assertTrue(assessment.titleWarning.contains("非无损"))
    }

    @Test
    fun testScannedPdfToDocx_isOcrRebuild() {
        val assessment = ConversionQualityAdvisor.assessConversion(
            sourceFormat = DocumentFormat.PDF,
            targetFormat = DocumentFormat.DOCX,
            isScannedPdfOrImage = true
        )

        assertEquals(ConversionTier.OCR_REBUILD, assessment.tier)
        assertTrue(assessment.isOcrRecommended)
        assertTrue(assessment.detailedNotice.contains("PP-OCR"))
    }

    @Test
    fun testImageToPdf_isLosslessContainer() {
        val assessment = ConversionQualityAdvisor.assessConversion(
            sourceFormat = DocumentFormat.IMAGE,
            targetFormat = DocumentFormat.PDF
        )

        assertEquals(ConversionTier.LOSSLESS, assessment.tier)
        assertEquals(100, assessment.fidelityPercentageEstimate)
    }
}
