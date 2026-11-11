package com.openofficepdf.feature.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.database.dao.TaskDao
import com.openofficepdf.core.model.DocumentFormat
import com.openofficepdf.engine.converter.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ConverterUiState(
    val selectedSourceUri: Uri? = null,
    val sourceFileName: String = "",
    val sourceFormat: DocumentFormat = DocumentFormat.PDF,
    val targetFormat: DocumentFormat = DocumentFormat.DOCX,
    val isScanned: Boolean = false,
    val qualityAssessment: QualityAssessment = ConversionQualityAdvisor.assessConversion(DocumentFormat.PDF, DocumentFormat.DOCX),
    val isConverting: Boolean = false,
    val currentTaskId: String? = null,
    val currentProgress: Float = 0f,
    val currentStageText: String = "",
    val completedPages: Int = 0,
    val totalPages: Int = 0,
    val errorMessage: String? = null,
    val toastMessage: String? = null
)

class ConverterViewModel(
    private val conversionEngine: ConversionEngine,
    private val taskDao: TaskDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var conversionJob: Job? = null

    fun selectSourceDocument(uri: Uri, fileName: String, format: DocumentFormat, isScanned: Boolean = false) {
        val target = if (format == DocumentFormat.PDF) DocumentFormat.DOCX else DocumentFormat.PDF
        val assessment = ConversionQualityAdvisor.assessConversion(format, target, isScanned)

        _uiState.value = _uiState.value.copy(
            selectedSourceUri = uri,
            sourceFileName = fileName,
            sourceFormat = format,
            targetFormat = target,
            isScanned = isScanned,
            qualityAssessment = assessment
        )
    }

    fun setTargetFormat(target: DocumentFormat) {
        val currentSource = _uiState.value.sourceFormat
        val assessment = ConversionQualityAdvisor.assessConversion(
            currentSource,
            target,
            _uiState.value.isScanned
        )
        _uiState.value = _uiState.value.copy(
            targetFormat = target,
            qualityAssessment = assessment
        )
    }

    fun startConversion() {
        val state = _uiState.value
        val sourceUri = state.selectedSourceUri ?: return
        val taskId = UUID.randomUUID().toString()
        val outputUri = Uri.parse("file:///dummy_output_${System.currentTimeMillis()}.${state.targetFormat.name.lowercase()}")

        val options = ConversionOptions(
            tier = state.qualityAssessment.tier,
            enableOcrForScannedPages = state.isScanned
        )

        _uiState.value = _uiState.value.copy(
            isConverting = true,
            currentTaskId = taskId,
            currentProgress = 0f,
            currentStageText = "任务已提交，准备开始转换..."
        )

        conversionJob = viewModelScope.launch {
            try {
                conversionEngine.executeConversion(
                    taskId = taskId,
                    sourceUri = sourceUri,
                    sourceFormat = state.sourceFormat,
                    targetFormat = state.targetFormat,
                    outputUri = outputUri,
                    options = options
                ).collect { progress ->
                    _uiState.value = _uiState.value.copy(
                        currentProgress = progress.progress,
                        currentStageText = progress.stageDescription,
                        completedPages = progress.currentPage,
                        totalPages = progress.totalPages
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    toastMessage = "转换已完成！文件已存入本地工作目录"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    errorMessage = "转换中断或取消: ${e.message}"
                )
            }
        }
    }

    fun cancelConversion() {
        val taskId = _uiState.value.currentTaskId ?: return
        viewModelScope.launch {
            conversionEngine.cancelConversion(taskId)
            conversionJob?.cancel()
            _uiState.value = _uiState.value.copy(isConverting = false, toastMessage = "转换已暂停/取消")
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
