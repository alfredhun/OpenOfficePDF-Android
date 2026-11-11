package com.openofficepdf.engine.converter

import android.net.Uri
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentFormat
import kotlinx.coroutines.flow.Flow

enum class ConversionTier {
    LOSSLESS,          // 原生导出/高保真封装 (如 DOCX/XLSX -> PDF, 图片多张 -> PDF)
    LAYOUT_REBUILD,    // 版面结构逆向重建 (如 PDF 文本提取 -> DOCX, SLANet 矢量网格 -> XLSX)
    OCR_REBUILD        // OCR 离线识别与排版还原 (如 扫描图片/扫描件 PDF -> DOCX/XLSX)
}

data class ConversionOptions(
    val tier: ConversionTier,
    val rasterDpi: Int = 300,                  // PDF 转图时的 DPI 选项 (150/300)
    val enableOcrForScannedPages: Boolean = false,
    val preserveImages: Boolean = true
)

data class TaskProgress(
    val taskId: String,
    val currentPage: Int,
    val totalPages: Int,
    val progress: Float,                       // 0.0f ~ 1.0f
    val stageDescription: String
)

/**
 * 多格式统一转换流水线引擎接口 (任务书 2.6 & 7.3 节)
 */
interface ConversionEngine {
    /**
     * 执行单文件或批量流式转换，每完成一页输出进度并持久化 Checkpoint
     */
    fun executeConversion(
        taskId: String,
        sourceUri: Uri,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat,
        outputUri: Uri,
        options: ConversionOptions
    ): Flow<TaskProgress>

    suspend fun cancelConversion(taskId: String): EngineResult<Unit>
}
