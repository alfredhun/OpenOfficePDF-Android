package com.openofficepdf.feature.ocr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.TaskDao
import com.openofficepdf.core.database.entity.OcrTaskEntity
import com.openofficepdf.engine.ocr.OcrEngine
import com.openofficepdf.engine.ocr.OcrPageProgress
import com.openofficepdf.engine.ocr.OcrPageResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class OcrUiState(
    val taskId: String? = null,
    val sourceUri: Uri? = null,
    val documentTitle: String = "未选择文档",
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val completedPages: Int = 0,
    val totalPages: Int = 0,
    val pageResults: List<OcrPageResult> = emptyList(),
    val lowConfidenceCount: Int = 0, // 置信度 < 0.80 的待校对文字数量
    val isSynthesizing: Boolean = false,
    val toastMessage: String? = null,
    val error: String? = null
)

class OcrViewModel(
    private val ocrEngine: OcrEngine,
    private val taskDao: TaskDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private var ocrJob: Job? = null

    fun selectDocument(uri: Uri, title: String) {
        _uiState.value = _uiState.value.copy(
            sourceUri = uri,
            documentTitle = title,
            completedPages = 0,
            totalPages = 1,
            progressFraction = 0f,
            pageResults = emptyList(),
            lowConfidenceCount = 0,
            error = null,
            toastMessage = "已选中文件: $title"
        )
    }

    fun startRecognition(enableTable: Boolean) {
        val uri = _uiState.value.sourceUri ?: return
        val taskId = UUID.randomUUID().toString()
        _uiState.value = _uiState.value.copy(
            taskId = taskId,
            isRunning = true,
            error = null,
            pageResults = emptyList(),
            completedPages = 0
        )

        ocrJob = viewModelScope.launch {
            try {
                ocrEngine.recognizeDocumentPages(
                    taskId = taskId,
                    sourceUri = uri,
                    pageRange = null,
                    enableLayoutAndTableAnalysis = enableTable
                ).collect { progress ->
                    val updatedResults = _uiState.value.pageResults + progress.pageResult
                    val lowConf = updatedResults.sumOf { page ->
                        page.textBlocks.sumOf { block ->
                            block.characters.count { it.confidence < 0.80f }
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        completedPages = progress.pageIndex + 1,
                        totalPages = progress.totalPages,
                        progressFraction = (progress.pageIndex + 1).toFloat() / progress.totalPages.coerceAtLeast(1),
                        pageResults = updatedResults,
                        lowConfidenceCount = lowConf
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    toastMessage = "离线识别完成，已处理 ${_uiState.value.completedPages} 页"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRunning = false, error = e.message)
            }
        }
    }

    fun synthesizeSearchablePdf(outputUri: Uri) {
        val sourceUri = _uiState.value.sourceUri ?: return
        val pages = _uiState.value.pageResults
        if (pages.isEmpty()) return

        _uiState.value = _uiState.value.copy(isSynthesizing = true)
        viewModelScope.launch {
            val res = ocrEngine.synthesizeSearchablePdf(sourceUri, pages, outputUri)
            if (res is EngineResult.Success) {
                _uiState.value = _uiState.value.copy(
                    isSynthesizing = false,
                    toastMessage = "双层可搜索 PDF 合成成功！"
                )
            } else if (res is EngineResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    isSynthesizing = false,
                    error = "双层 PDF 合成失败: ${res.errorMessage}"
                )
            }
        }
    }

    fun pauseOrCancel() {
        ocrJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false, toastMessage = "已暂停/取消")
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
