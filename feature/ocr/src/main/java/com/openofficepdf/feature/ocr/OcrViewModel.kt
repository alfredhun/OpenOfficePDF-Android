package com.openofficepdf.feature.ocr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val completedPages: Int = 0,
    val totalPages: Int = 0,
    val pageResults: List<OcrPageResult> = emptyList(),
    val lowConfidenceCount: Int = 0, // 置信度 < 0.80 的待校对文字数量
    val error: String? = null
)

class OcrViewModel(
    private val ocrEngine: OcrEngine,
    private val taskDao: TaskDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private var ocrJob: Job? = null

    fun startRecognition(sourceUri: Uri, totalPages: Int, enableTable: Boolean) {
        val taskId = UUID.randomUUID().toString()
        _uiState.value = OcrUiState(
            taskId = taskId,
            sourceUri = sourceUri,
            isRunning = true,
            totalPages = totalPages
        )

        ocrJob = viewModelScope.launch {
            try {
                ocrEngine.recognizeDocumentPages(
                    taskId = taskId,
                    sourceUri = sourceUri,
                    pageRange = 0 until totalPages,
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
                        progressFraction = (progress.pageIndex + 1).toFloat() / progress.totalPages,
                        pageResults = updatedResults,
                        lowConfidenceCount = lowConf
                    )
                }
                _uiState.value = _uiState.value.copy(isRunning = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRunning = false, error = e.message)
            }
        }
    }

    fun pauseOrCancel() {
        ocrJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }
}
