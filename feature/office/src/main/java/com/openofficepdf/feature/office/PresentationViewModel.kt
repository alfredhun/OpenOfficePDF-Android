package com.openofficepdf.feature.office

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.engine.office.OfficeEngine
import com.openofficepdf.engine.office.SlideInkStroke
import com.openofficepdf.engine.office.SlidePage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class SlideInteractionMode {
    VIEW,       // 翻页与浏览
    INK_PEN,    // 荧光笔/红笔手写批注
    ERASER      // 批注橡皮擦
}

data class PresentationUiState(
    val sessionId: SessionId? = null,
    val documentTitle: String = "演示文稿.pptx",
    val slides: List<SlidePage> = emptyList(),
    val currentSlideIndex: Int = 0,
    val isFullscreenPlaying: Boolean = false,
    val interactionMode: SlideInteractionMode = SlideInteractionMode.VIEW,
    val activeInkColor: Int = 0xFFFF0000.toInt(), // 默认红笔
    val isLoading: Boolean = false,
    val toastMessage: String? = null
)

class PresentationViewModel(
    private val officeEngine: OfficeEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresentationUiState())
    val uiState: StateFlow<PresentationUiState> = _uiState.asStateFlow()

    init {
        // 初始化演示幻灯片示例
        setupSamplePresentation()
    }

    private fun setupSamplePresentation() {
        val sampleSlides = listOf(
            SlidePage(slideIndex = 0, title = "OpenOfficePDF 架构设计与离线规划", notes = "开场介绍项目愿景"),
            SlidePage(slideIndex = 1, title = "六大核心离线工作台矩阵", notes = "PDF / Word / Excel / Markdown / OCR / 转换"),
            SlidePage(slideIndex = 2, title = "16KB ELF 页面对齐与硬件适配", notes = "Android 15+ 强制标准"),
            SlidePage(slideIndex = 3, title = "原格式保护与三阶段事务保存", notes = "严格保证用户原始文件零破坏")
        )
        _uiState.value = _uiState.value.copy(slides = sampleSlides)
    }

    fun openPresentation(docId: DocumentId, title: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, documentTitle = title)
            when (val result = officeEngine.openSession(docId, uri)) {
                is EngineResult.Success -> {
                    val session = result.data
                    val parsed = session.parsedPptx
                    if (parsed != null && parsed.slides.isNotEmpty()) {
                        val realSlides = parsed.slides.mapIndexed { idx, slide ->
                            SlidePage(
                                slideIndex = idx,
                                title = slide.title,
                                notes = slide.paragraphs.joinToString("\n"),
                                widthPt = parsed.slideWidthPt,
                                heightPt = parsed.slideHeightPt,
                                imageBytesList = slide.imageBytesList
                            )
                        }
                        _uiState.value = _uiState.value.copy(
                            sessionId = session.sessionId,
                            documentTitle = title,
                            slides = realSlides,
                            currentSlideIndex = 0,
                            isLoading = false,
                            toastMessage = "已加载 ${realSlides.size} 页幻灯片"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            sessionId = session.sessionId,
                            isLoading = false
                        )
                    }
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "打开 PPT 失败: ${result.errorMessage}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun switchSlide(index: Int) {
        if (index in _uiState.value.slides.indices) {
            _uiState.value = _uiState.value.copy(currentSlideIndex = index)
        }
    }

    fun nextSlide() {
        val next = _uiState.value.currentSlideIndex + 1
        if (next < _uiState.value.slides.size) {
            _uiState.value = _uiState.value.copy(currentSlideIndex = next)
        }
    }

    fun prevSlide() {
        val prev = _uiState.value.currentSlideIndex - 1
        if (prev >= 0) {
            _uiState.value = _uiState.value.copy(currentSlideIndex = prev)
        }
    }

    fun toggleFullscreenPlay() {
        _uiState.value = _uiState.value.copy(
            isFullscreenPlaying = !_uiState.value.isFullscreenPlaying,
            interactionMode = SlideInteractionMode.VIEW
        )
    }

    fun setInteractionMode(mode: SlideInteractionMode) {
        _uiState.value = _uiState.value.copy(interactionMode = mode)
    }

    fun setInkColor(colorArgb: Int) {
        _uiState.value = _uiState.value.copy(activeInkColor = colorArgb)
    }

    /**
     * 记录并保存当前幻灯片的手写墨迹批注
     */
    fun addInkStroke(points: List<Pair<Float, Float>>) {
        if (points.size < 2) return
        val currentIdx = _uiState.value.currentSlideIndex
        val currentSlide = _uiState.value.slides.getOrNull(currentIdx) ?: return

        val newStroke = SlideInkStroke(
            strokeId = UUID.randomUUID().toString(),
            points = points,
            colorArgb = _uiState.value.activeInkColor
        )

        val updatedSlide = currentSlide.copy(strokes = currentSlide.strokes + newStroke)
        val updatedSlides = _uiState.value.slides.toMutableList()
        updatedSlides[currentIdx] = updatedSlide

        _uiState.value = _uiState.value.copy(slides = updatedSlides)
    }

    fun clearCurrentSlideStrokes() {
        val currentIdx = _uiState.value.currentSlideIndex
        val currentSlide = _uiState.value.slides.getOrNull(currentIdx) ?: return
        val updatedSlide = currentSlide.copy(strokes = emptyList())
        val updatedSlides = _uiState.value.slides.toMutableList()
        updatedSlides[currentIdx] = updatedSlide
        _uiState.value = _uiState.value.copy(slides = updatedSlides, toastMessage = "已清空当前页批注")
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
