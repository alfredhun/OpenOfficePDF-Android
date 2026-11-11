package com.openofficepdf.engine.ocr

import android.graphics.RectF
import android.net.Uri
import com.openofficepdf.core.common.EngineResult
import kotlinx.coroutines.flow.Flow

data class OcrCharacterBox(
    val char: String,
    val bounds: RectF,
    val confidence: Float        // 置信度评分，< 0.80 触发 UI 高亮校对
)

data class OcrTextBlock(
    val text: String,
    val bounds: RectF,
    val confidence: Float,
    val characters: List<OcrCharacterBox>
)

data class OcrPageResult(
    val pageIndex: Int,
    val textBlocks: List<OcrTextBlock>,
    val detectedTableCount: Int,
    val deskewAngleDeg: Float
)

data class OcrPageProgress(
    val pageIndex: Int,
    val totalPages: Int,
    val pageResult: OcrPageResult
)

interface OcrEngine {
    /**
     * 按页流式输出识别与版面分析结果，每完成一页自动写入 Checkpoint
     */
    fun recognizeDocumentPages(
        taskId: String,
        sourceUri: Uri,
        pageRange: IntRange?,
        enableLayoutAndTableAnalysis: Boolean
    ): Flow<OcrPageProgress>

    suspend fun synthesizeSearchablePdf(
        sourcePdfUri: Uri,
        ocrPages: List<OcrPageResult>,
        outputUri: Uri
    ): EngineResult<Unit>
}
