package com.openofficepdf.engine.pdf

import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.model.pdf.EditCapability
import com.openofficepdf.core.model.pdf.PdfPathObject
import com.openofficepdf.core.model.pdf.PdfTextObject
import com.openofficepdf.core.model.pdf.createRectF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PdfEditEngineImplTest {

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
    fun testApplyOverlayEdit_success() = runBlocking {
        val sessionId = SessionId("test_session_overlay")
        val pageIndex = 0
        val targetRegion = createRectF(100f, 200f, 300f, 250f)
        val maskColor = 0xFFFFFFFF.toInt()
        val textColor = 0xFF000000.toInt()
        val text = "这是覆盖测试文本"

        val result = editEngine.applyOverlayEdit(
            sessionId = sessionId,
            pageIndex = pageIndex,
            targetRegionPt = targetRegion,
            backgroundMaskColorArgb = maskColor,
            newText = text,
            fontSizePt = 14f,
            textColorArgb = textColor
        )

        assertTrue(result is EngineResult.Success)
        val textObj = (result as EngineResult.Success).data as PdfTextObject
        assertEquals(text, textObj.text)
        assertEquals(EditCapability.OVERLAY_TEXT, textObj.capability)
        assertEquals(14f, textObj.fontSizePt, 0.01f)

        // 验证页面对象树中同时包含遮罩 Path 与文字
        val objectsResult = editEngine.getPageObjects(sessionId, pageIndex)
        assertTrue(objectsResult is EngineResult.Success)
        val list = (objectsResult as EngineResult.Success).data
        assertEquals(2, list.size)

        val maskObj = list[0] as PdfPathObject
        assertEquals(EditCapability.OVERLAY_MASK, maskObj.capability)
        assertEquals(maskColor, maskObj.fillColorArgb)
    }

    @Test
    fun testApplyOverlayEdit_invalidRegionFails() = runBlocking {
        val sessionId = SessionId("test_session_empty")
        val invalidRegion = createRectF(0f, 0f, 0f, 0f)

        val result = editEngine.applyOverlayEdit(
            sessionId = sessionId,
            pageIndex = 0,
            targetRegionPt = invalidRegion,
            backgroundMaskColorArgb = 0xFFFFFFFF.toInt(),
            newText = "test",
            fontSizePt = 12f,
            textColorArgb = 0xFF000000.toInt()
        )

        assertTrue(result is EngineResult.Failure)
    }
}
