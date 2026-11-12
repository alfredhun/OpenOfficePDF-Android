package com.openofficepdf.engine.pdf

import android.graphics.Matrix
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.model.pdf.EditCapability
import com.openofficepdf.core.model.pdf.PdfTextObject
import com.openofficepdf.core.model.pdf.createRectF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfInPlaceAndRedactionTest {

    private lateinit var editEngine: PdfEditEngineImpl
    private lateinit var fontManager: PdfFontManager
    private lateinit var redactionService: RedactionService

    @Before
    fun setUp() {
        fontManager = PdfFontManager()
        redactionService = RedactionService()
        editEngine = PdfEditEngineImpl(fontManager, redactionService)
    }

    @Test
    fun testInPlaceTextObjectUpdate_withFontFallback() = runBlocking {
        val sessionId = SessionId("test_session_inplace")
        val pageIndex = 0

        // 先注入一个原有文字对象
        val textObj = PdfTextObject(
            id = "txt_original",
            pageIndex = pageIndex,
            boundsPt = createRectF(100f, 100f, 250f, 120f),
            transform = Matrix(),
            text = "原始文字",
            fontSizePt = 12f,
            fontName = "SimSun",
            colorArgb = 0xFF000000.toInt(),
            charSpacingPt = 0f,
            wordSpacingPt = 0f,
            capability = EditCapability.IN_PLACE_TEXT
        )

        // 模拟页面对象
        val field = PdfEditEngineImpl::class.java.getDeclaredField("pageObjectsMap")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(editEngine) as MutableMap<Pair<SessionId, Int>, MutableList<com.openofficepdf.core.model.pdf.PdfObjectModel>>
        map[sessionId to pageIndex] = mutableListOf(textObj)

        // 执行原位修改
        val updateResult = editEngine.updateTextObject(
            sessionId = sessionId,
            pageIndex = pageIndex,
            objectId = "txt_original",
            newText = "修改后的新文本",
            newFontSizePt = 14f,
            newColorArgb = null
        )

        assertTrue(updateResult is EngineResult.Success)
        val outcome = (updateResult as EngineResult.Success).data
        assertEquals("修改后的新文本", outcome.updatedObject.text)
        assertEquals(14f, outcome.updatedObject.fontSizePt, 0.01f)
    }

    @Test
    fun testTrueRedaction_purgesObjectsAndGeneratesReport() = runBlocking {
        val sessionId = SessionId("test_session_redact")
        val pageIndex = 0

        // 注入待脱敏的敏感文字对象
        val secretObj = PdfTextObject(
            id = "txt_secret",
            pageIndex = pageIndex,
            boundsPt = createRectF(100f, 100f, 200f, 150f),
            transform = Matrix(),
            text = "绝密账号密码: 123456",
            fontSizePt = 12f,
            fontName = "SimSun",
            colorArgb = 0xFF000000.toInt(),
            charSpacingPt = 0f,
            wordSpacingPt = 0f,
            capability = EditCapability.IN_PLACE_TEXT
        )

        val field = PdfEditEngineImpl::class.java.getDeclaredField("pageObjectsMap")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(editEngine) as MutableMap<Pair<SessionId, Int>, MutableList<com.openofficepdf.core.model.pdf.PdfObjectModel>>
        map[sessionId to pageIndex] = mutableListOf(secretObj)

        // 执行真 Redaction 脱敏 (相交区域)
        val redactRegion = createRectF(90f, 90f, 210f, 160f)
        val redactResult = editEngine.applyTrueRedaction(
            sessionId = sessionId,
            pageIndex = pageIndex,
            redactRegionsPt = listOf(redactRegion)
        )

        assertTrue(redactResult is EngineResult.Success)
        val outcome = (redactResult as EngineResult.Success).data
        assertTrue(outcome.removedObjectCount >= 1)
        assertTrue(outcome.verificationReport.postSaveSearchVerifiedClean)

        // 验证内存对象已被物理移除
        val remainingObjects = (editEngine.getPageObjects(sessionId, pageIndex) as EngineResult.Success).data
        assertEquals(0, remainingObjects.size)
    }

    @Test
    fun testRedactionVerificationAssertion_failsWhenResidualFound() {
        val tempFile = File(System.getProperty("java.io.tmpdir"), "dirty_pdf.pdf")
        tempFile.writeText("%PDF-1.7 含有未抹除的机密文字 123456 %%EOF")

        val result = redactionService.verifySanitizedDocument(tempFile, "123456")
        assertTrue(result is EngineResult.Failure)
        assertEquals(com.openofficepdf.core.common.EngineErrorCode.REDACTION_VERIFICATION_FAILED, (result as EngineResult.Failure).errorCode)

        tempFile.delete()
    }
}
