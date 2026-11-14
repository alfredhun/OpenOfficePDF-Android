package com.openofficepdf.feature.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.SessionAndEditDao
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.model.pdf.EditCapability
import com.openofficepdf.core.model.pdf.PdfObjectModel
import com.openofficepdf.core.model.pdf.PdfTextObject
import com.openofficepdf.core.storage.DocumentAccessProvider
import com.openofficepdf.core.storage.TemporaryFileManager
import com.openofficepdf.core.storage.TransactionalFileCommitter
import com.openofficepdf.engine.ocr.PdfTextContentExtractor
import com.openofficepdf.engine.pdf.AndroidPdfRendererBridge
import com.openofficepdf.engine.pdf.PdfEditEngine
import com.openofficepdf.engine.pdf.PdfTileRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class PdfInteractionMode {
    VIEW,            // 普通手势浏览（平移、缩放、自适应全屏）
    SELECT_TEXT,     // 文本选择
    OVERLAY_EDIT,    // 一级策略：覆盖式编辑（带未清除底层文字红线警告）
    IN_PLACE_EDIT,   // 二级策略：对象级原位编辑
    TRUE_REDACTION   // 真 Redaction 内容物理抹除脱敏
}

data class OverlayEditDraft(
    val targetRegionPt: RectF,
    val backgroundMaskColorArgb: Int = 0xFFFFFFFF.toInt(),
    val text: String = "",
    val fontSizePt: Float = 12f,
    val textColorArgb: Int = 0xFF000000.toInt()
)

data class InPlaceEditDraft(
    val objectId: String,
    val initialText: String,
    val currentText: String,
    val fontSizePt: Float,
    val fontFallbackWarning: String? = null
)

data class PdfViewerUiState(
    val sessionId: SessionId? = null,
    val documentUri: Uri? = null,
    val documentTitle: String = "示例文件.pdf",
    val pageCount: Int = 1,
    val currentPageIndex: Int = 0,
    val zoomScale: Float = 1.0f,
    val isNightMode: Boolean = false,
    val interactionMode: PdfInteractionMode = PdfInteractionMode.VIEW,
    val renderedBitmap: Bitmap? = null,
    val rawPageWidthPt: Float = 595f,
    val rawPageHeightPt: Float = 842f,
    val activeOverlayDraft: OverlayEditDraft? = null,
    val activeInPlaceDraft: InPlaceEditDraft? = null,
    val selectedRedactRegions: List<RectF> = emptyList(),
    val overlayBannerWarningVisible: Boolean = false,
    val pageObjects: List<PdfObjectModel> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null
)

class PdfViewerViewModel(
    private val tileRenderer: PdfTileRenderer,
    private val editEngine: PdfEditEngine,
    private val sessionDao: SessionAndEditDao,
    private val storageProvider: DocumentAccessProvider,
    private val tempFileManager: TemporaryFileManager,
    private val transactionalCommitter: TransactionalFileCommitter,
    private val pdfRendererBridge: AndroidPdfRendererBridge? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    init {
        // 初始化预置对象供初始演示演练
        setupSampleObjects()
    }

    private fun setupSampleObjects() {
        val sampleObj = PdfTextObject(
            id = "txt_original_001",
            pageIndex = 0,
            boundsPt = RectF(50f, 100f, 350f, 130f),
            transform = android.graphics.Matrix(),
            text = "合同机密条款：受法律绝对保护",
            fontSizePt = 16f,
            fontName = "SimSun",
            colorArgb = 0xFF000000.toInt(),
            charSpacingPt = 0f,
            wordSpacingPt = 0f,
            capability = EditCapability.IN_PLACE_TEXT
        )
        _uiState.value = _uiState.value.copy(pageObjects = listOf(sampleObj))
    }

    /**
     * 打开真实外部 PDF 文档 (通过 SAF Uri)
     * 同时完成：原生高分辨率光栅化渲染 + 真实文本对象流解析 (支持原位点击编辑)
     */
    fun openPdfDocument(uri: Uri, title: String) {
        val newSessionId = SessionId("pdf_session_" + UUID.randomUUID().toString().take(8))
        viewModelScope.launch {
            if (pdfRendererBridge != null) {
                val openResult = pdfRendererBridge.openDocument(newSessionId, uri)
                if (openResult is EngineResult.Success) {
                    val count = openResult.data
                    _uiState.value = _uiState.value.copy(
                        sessionId = newSessionId,
                        documentUri = uri,
                        documentTitle = title,
                        pageCount = count,
                        currentPageIndex = 0,
                        toastMessage = "成功打开文档，共 $count 页"
                    )
                    renderCurrentPage(newSessionId, 0)
                    extractPageObjectsForEditing(uri, 0)
                } else if (openResult is EngineResult.Failure) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = openResult.errorMessage,
                        documentTitle = title
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    sessionId = newSessionId,
                    documentUri = uri,
                    documentTitle = title,
                    toastMessage = "已加载 $title"
                )
            }
        }
    }

    /**
     * 提取真实 PDF 页面中的所有文字对象并赋以包围盒，让用户能在屏幕上点选进行原位编辑
     */
    private fun extractPageObjectsForEditing(uri: Uri, pageIndex: Int) {
        viewModelScope.launch {
            try {
                storageProvider.openReadOnlyStream(uri).use { stream ->
                    val textBlocks: List<com.openofficepdf.engine.ocr.OcrTextBlock> = PdfTextContentExtractor.extractTextFromPdfStream(stream, pageIndex)
                    if (textBlocks.isNotEmpty()) {
                        val realObjects = textBlocks.mapIndexed { idx: Int, block: com.openofficepdf.engine.ocr.OcrTextBlock ->
                            PdfTextObject(
                                id = "real_txt_${pageIndex}_$idx",
                                pageIndex = pageIndex,
                                boundsPt = block.bounds,
                                transform = android.graphics.Matrix(),
                                text = block.text,
                                fontSizePt = (block.bounds.height()).coerceIn(10f, 36f),
                                fontName = "NotoSansCJK",
                                colorArgb = 0xFF000000.toInt(),
                                charSpacingPt = 0f,
                                wordSpacingPt = 0f,
                                capability = EditCapability.IN_PLACE_TEXT
                            )
                        }
                        _uiState.value = _uiState.value.copy(pageObjects = realObjects)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun renderCurrentPage(sessionId: SessionId, pageIndex: Int) {
        val bridge = pdfRendererBridge ?: return
        viewModelScope.launch {
            val size = bridge.getPageSize(sessionId, pageIndex) ?: (595f to 842f)
            val baseW = size.first.coerceAtLeast(1f)
            val baseH = size.second.coerceAtLeast(1f)

            // 修复：限制单张预览图最大边长为 2048px，防止超大工程图纸 PDF 引发 OOM
            val maxDimension = 2048f
            val desiredScale = 2.0f
            val safeScale = minOf(
                desiredScale,
                maxDimension / maxOf(baseW, baseH)
            ).coerceAtLeast(0.5f)

            val w = (baseW * safeScale).toInt().coerceAtLeast(100)
            val h = (baseH * safeScale).toInt().coerceAtLeast(100)

            // 若尺寸一致则复用现有 Bitmap，减少 GC 抖动
            val existingBitmap = _uiState.value.renderedBitmap
            val targetBitmap = if (existingBitmap != null &&
                !existingBitmap.isRecycled &&
                existingBitmap.width == w &&
                existingBitmap.height == h
            ) {
                existingBitmap
            } else {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }

            val renderRes = bridge.renderPageToBitmap(sessionId, pageIndex, targetBitmap)
            if (renderRes is EngineResult.Success) {
                _uiState.value = _uiState.value.copy(
                    renderedBitmap = targetBitmap,
                    rawPageWidthPt = baseW,
                    rawPageHeightPt = baseH,
                    currentPageIndex = pageIndex
                )
            }

            _uiState.value.documentUri?.let { uri ->
                extractPageObjectsForEditing(uri, pageIndex)
            }
        }
    }

    fun nextPage() {
        val s = _uiState.value
        val sid = s.sessionId ?: return
        if (s.currentPageIndex + 1 < s.pageCount) {
            renderCurrentPage(sid, s.currentPageIndex + 1)
        }
    }

    fun prevPage() {
        val s = _uiState.value
        val sid = s.sessionId ?: return
        if (s.currentPageIndex > 0) {
            renderCurrentPage(sid, s.currentPageIndex - 1)
        }
    }

    fun switchMode(mode: PdfInteractionMode) {
        val showOverlayWarning = (mode == PdfInteractionMode.OVERLAY_EDIT)
        _uiState.value = _uiState.value.copy(
            interactionMode = mode,
            overlayBannerWarningVisible = showOverlayWarning,
            activeOverlayDraft = if (mode == PdfInteractionMode.OVERLAY_EDIT) OverlayEditDraft(RectF()) else null,
            activeInPlaceDraft = null,
            toastMessage = when (mode) {
                PdfInteractionMode.IN_PLACE_EDIT -> "二级原位编辑模式：点击页面绿色框选文字直接修改"
                PdfInteractionMode.TRUE_REDACTION -> "真脱敏模式：框选区域后将从二进制中彻底物理清除"
                PdfInteractionMode.OVERLAY_EDIT -> "一级覆盖编辑：拖拽框选矩形后输入覆盖文字"
                else -> null
            }
        )
    }

    fun dismissOverlayWarning() {
        _uiState.value = _uiState.value.copy(overlayBannerWarningVisible = false)
    }

    fun toggleNightMode() {
        _uiState.value = _uiState.value.copy(isNightMode = !_uiState.value.isNightMode)
    }

    fun updateOverlayDraftBounds(regionPt: RectF) {
        val currentDraft = _uiState.value.activeOverlayDraft ?: OverlayEditDraft(regionPt)
        _uiState.value = _uiState.value.copy(
            activeOverlayDraft = currentDraft.copy(targetRegionPt = regionPt)
        )
    }

    fun selectTextObjectForInPlaceEdit(textObj: PdfTextObject) {
        _uiState.value = _uiState.value.copy(
            activeInPlaceDraft = InPlaceEditDraft(
                objectId = textObj.id,
                initialText = textObj.text,
                currentText = textObj.text,
                fontSizePt = textObj.fontSizePt
            )
        )
    }

    fun updateInPlaceEditText(newText: String) {
        val draft = _uiState.value.activeInPlaceDraft ?: return
        _uiState.value = _uiState.value.copy(
            activeInPlaceDraft = draft.copy(currentText = newText)
        )
    }

    /**
     * 提交二级原位编辑
     */
    fun commitInPlaceEdit() {
        val draft = _uiState.value.activeInPlaceDraft ?: return

        // 立即在前端内存中更新对应文本对象与显示
        val list = _uiState.value.pageObjects.toMutableList()
        val idx = list.indexOfFirst { it.id == draft.objectId }
        if (idx != -1) {
            val orig = list[idx]
            if (orig is PdfTextObject) {
                list[idx] = orig.copy(text = draft.currentText)
            }
        }

        _uiState.value = _uiState.value.copy(
            pageObjects = list,
            activeInPlaceDraft = null,
            toastMessage = "原位修改已应用并实时更新页面"
        )
    }

    /**
     * 真 Redaction 脱敏操作：框选物理抹除区域
     */
    fun addRedactionRegion(regionPt: RectF) {
        if (regionPt.isEmpty) return
        val updated = _uiState.value.selectedRedactRegions + regionPt
        _uiState.value = _uiState.value.copy(selectedRedactRegions = updated)
    }

    /**
     * 执行真 Redaction 脱敏提交（物理清除文本流并重构 Content Stream）
     */
    fun commitTrueRedaction() {
        val regions = _uiState.value.selectedRedactRegions
        if (regions.isEmpty()) return
        val sessionId = _uiState.value.sessionId ?: SessionId("session_pdf_001")
        val pageIndex = _uiState.value.currentPageIndex

        viewModelScope.launch {
            val result = editEngine.applyTrueRedaction(
                sessionId = sessionId,
                pageIndex = pageIndex,
                redactRegionsPt = regions
            )

            when (result) {
                is EngineResult.Success -> {
                    val outcome = result.data
                    val objectsResult = editEngine.getPageObjects(sessionId, pageIndex)
                    val newObjs = if (objectsResult is EngineResult.Success) objectsResult.data else emptyList()

                    _uiState.value = _uiState.value.copy(
                        interactionMode = PdfInteractionMode.VIEW,
                        selectedRedactRegions = emptyList(),
                        pageObjects = newObjs,
                        toastMessage = "安全脱敏完成：已物理清除 ${outcome.removedObjectCount} 个对象，并通过闭环自检断言"
                    )
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(errorMessage = "脱敏失败: ${result.errorMessage}")
                }
                is EngineResult.Cancelled -> Unit
            }
        }
    }

    fun commitOverlayEdit() {
        val state = _uiState.value
        val draft = state.activeOverlayDraft ?: return
        val sessionId = state.sessionId ?: SessionId("session_pdf_001")
        val pageIndex = state.currentPageIndex

        if (draft.targetRegionPt.isEmpty || draft.text.isBlank()) return

        // 内存中实时新增覆盖文本对象
        val newObj = PdfTextObject(
            id = "overlay_txt_" + UUID.randomUUID().toString().take(6),
            pageIndex = pageIndex,
            boundsPt = draft.targetRegionPt,
            transform = android.graphics.Matrix(),
            text = draft.text,
            fontSizePt = draft.fontSizePt,
            fontName = "NotoSansCJK",
            colorArgb = draft.textColorArgb,
            charSpacingPt = 0f,
            wordSpacingPt = 0f,
            capability = EditCapability.OVERLAY_TEXT
        )

        val updatedList = _uiState.value.pageObjects + newObj
        _uiState.value = _uiState.value.copy(
            interactionMode = PdfInteractionMode.VIEW,
            activeOverlayDraft = null,
            pageObjects = updatedList,
            toastMessage = "已应用覆盖编辑 (注意：底层文字未被物理抹除)"
        )
    }

    fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        val sid = _uiState.value.sessionId
        if (sid != null) {
            pdfRendererBridge?.closeDocument(sid)
        }
        _uiState.value.renderedBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}
