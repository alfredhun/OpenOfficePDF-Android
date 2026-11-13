package com.openofficepdf.feature.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.SessionAndEditDao
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.model.pdf.PdfObjectModel
import com.openofficepdf.core.model.pdf.PdfTextObject
import com.openofficepdf.core.storage.DocumentAccessProvider
import com.openofficepdf.core.storage.TemporaryFileManager
import com.openofficepdf.core.storage.TransactionalFileCommitter
import com.openofficepdf.engine.pdf.AndroidPdfRendererBridge
import com.openofficepdf.engine.pdf.PdfEditEngine
import com.openofficepdf.engine.pdf.PdfTileRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class PdfInteractionMode {
    VIEW,            // 普通手势浏览（平移、缩放）
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
        // 初始化预置对象供二级原位编辑演练
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
            capability = com.openofficepdf.core.model.pdf.EditCapability.IN_PLACE_TEXT
        )
        _uiState.value = _uiState.value.copy(pageObjects = listOf(sampleObj))
    }

    /**
     * 打开真实外部 PDF 文档 (通过 SAF Uri)
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

    fun renderCurrentPage(sessionId: SessionId, pageIndex: Int) {
        val bridge = pdfRendererBridge ?: return
        viewModelScope.launch {
            val size = bridge.getPageSize(sessionId, pageIndex) ?: (595f to 842f)
            val w = size.first.toInt().coerceAtLeast(100)
            val h = size.second.toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val renderRes = bridge.renderPageToBitmap(sessionId, pageIndex, bitmap)
            if (renderRes is EngineResult.Success) {
                _uiState.value = _uiState.value.copy(
                    renderedBitmap = bitmap,
                    currentPageIndex = pageIndex
                )
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
                PdfInteractionMode.IN_PLACE_EDIT -> "二级原位编辑模式：点击文本对象修改文字内容"
                PdfInteractionMode.TRUE_REDACTION -> "真脱敏模式：框选区域后将从二进制中彻底物理清除"
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

    fun updateOverlayDraftText(text: String, fontSizePt: Float, textColorArgb: Int, maskColorArgb: Int) {
        val currentDraft = _uiState.value.activeOverlayDraft ?: return
        _uiState.value = _uiState.value.copy(
            activeOverlayDraft = currentDraft.copy(
                text = text,
                fontSizePt = fontSizePt,
                textColorArgb = textColorArgb,
                backgroundMaskColorArgb = maskColorArgb
            )
        )
    }

    /**
     * 二级策略：点击页面原生文字对象，触发原位修改弹窗
     */
    fun selectTextObjectForInPlaceEdit(obj: PdfTextObject) {
        _uiState.value = _uiState.value.copy(
            activeInPlaceDraft = InPlaceEditDraft(
                objectId = obj.id,
                initialText = obj.text,
                currentText = obj.text,
                fontSizePt = obj.fontSizePt
            )
        )
    }

    fun updateInPlaceText(newText: String) {
        val current = _uiState.value.activeInPlaceDraft ?: return
        _uiState.value = _uiState.value.copy(activeInPlaceDraft = current.copy(currentText = newText))
    }

    /**
     * 提交二级原位编辑（自动进行子集字体检测与开源思源字体回退提示）
     */
    fun commitInPlaceEdit() {
        val draft = _uiState.value.activeInPlaceDraft ?: return
        val sessionId = _uiState.value.sessionId ?: SessionId("session_pdf_001")
        val pageIndex = _uiState.value.currentPageIndex

        viewModelScope.launch {
            val result = editEngine.updateTextObject(
                sessionId = sessionId,
                pageIndex = pageIndex,
                objectId = draft.objectId,
                newText = draft.currentText
            )

            when (result) {
                is EngineResult.Success -> {
                    val outcome = result.data
                    val warning = if (outcome.fontFallbackApplied) {
                        "原嵌入字体缺少部分字符，已自动替换为内置思源中文字体"
                    } else null

                    // 刷新对象树
                    val list = _uiState.value.pageObjects.toMutableList()
                    val idx = list.indexOfFirst { it.id == draft.objectId }
                    if (idx != -1) {
                        list[idx] = outcome.updatedObject
                    }

                    _uiState.value = _uiState.value.copy(
                        pageObjects = list,
                        activeInPlaceDraft = null,
                        toastMessage = warning ?: "原位修改成功"
                    )
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.errorMessage)
                }
                is EngineResult.Cancelled -> Unit
            }
        }
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

        viewModelScope.launch {
            val result = editEngine.applyOverlayEdit(
                sessionId = sessionId,
                pageIndex = pageIndex,
                targetRegionPt = draft.targetRegionPt,
                backgroundMaskColorArgb = draft.backgroundMaskColorArgb,
                newText = draft.text,
                fontSizePt = draft.fontSizePt,
                textColorArgb = draft.textColorArgb
            )

            when (result) {
                is EngineResult.Success -> {
                    val objectsResult = editEngine.getPageObjects(sessionId, pageIndex)
                    val newObjects = if (objectsResult is EngineResult.Success) objectsResult.data else emptyList()

                    _uiState.value = _uiState.value.copy(
                        interactionMode = PdfInteractionMode.VIEW,
                        activeOverlayDraft = null,
                        pageObjects = newObjects,
                        toastMessage = "已应用覆盖编辑 (注意：底层文字未被物理抹除)"
                    )
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.errorMessage)
                }
                is EngineResult.Cancelled -> Unit
            }
        }
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
    }
}
