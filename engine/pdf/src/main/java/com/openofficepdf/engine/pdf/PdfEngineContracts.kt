package com.openofficepdf.engine.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.model.pdf.*
import kotlinx.coroutines.flow.Flow

/**
 * PDF 渲染引擎核心接口 (任务书 7.2 节)
 */
interface PdfRenderEngine {
    /**
     * 渲染指定页面的局部瓦片至复用池提供的 targetBitmap
     * @param tileRect 处于 Normalized Page Space (单位: pt) 的裁剪区域
     * @param scale 缩放倍率
     */
    suspend fun renderTile(
        sessionId: SessionId,
        pageIndex: Int,
        tileRect: RectF,
        scale: Float,
        nightMode: Boolean,
        targetBitmap: Bitmap
    ): EngineResult<Unit>
}

/**
 * PDF 全文搜索接口
 */
interface PdfSearchEngine {
    /**
     * 流式全文搜索，每解析完一页立即 emit 命中结果，支持协程取消
     */
    fun search(
        sessionId: SessionId,
        keyword: String,
        matchCase: Boolean = false
    ): Flow<SearchMatch>
}

/**
 * PDF 对象级编辑、覆盖编辑与真脱敏接口 (任务书 2.1.2, 2.1.3 & 7.2 节)
 */
interface PdfEditEngine {
    /**
     * 按需解析并获取指定页面的所有可交互对象 (Normalized Page Space)
     */
    suspend fun getPageObjects(
        sessionId: SessionId,
        pageIndex: Int
    ): EngineResult<List<PdfObjectModel>>

    /**
     * 二级策略：修改原有文字对象内容与样式（自动处理子集字体检测与开源中文字体回退）
     */
    suspend fun updateTextObject(
        sessionId: SessionId,
        pageIndex: Int,
        objectId: String,
        newText: String,
        newFontSizePt: Float? = null,
        newColorArgb: Int? = null
    ): EngineResult<TextEditOutcome>

    /**
     * 一级策略：覆盖式编辑（保底交付模式，带有显式底层文字未清除提示契约）
     */
    suspend fun applyOverlayEdit(
        sessionId: SessionId,
        pageIndex: Int,
        targetRegionPt: RectF,
        backgroundMaskColorArgb: Int,
        newText: String,
        fontSizePt: Float,
        textColorArgb: Int
    ): EngineResult<PdfObjectModel>

    /**
     * 修改或替换原有图片对象
     */
    suspend fun updateImageObject(
        sessionId: SessionId,
        pageIndex: Int,
        objectId: String,
        newBoundsPt: RectF? = null,
        newTransform: Matrix? = null,
        replacementImageUri: Uri? = null
    ): EngineResult<PdfImageObject>

    /**
     * 真正的 PDF Redaction（物理移除相交文本流、擦除涂黑位图像素、清理残留引用，并闭环验证）
     */
    suspend fun applyTrueRedaction(
        sessionId: SessionId,
        pageIndex: Int,
        redactRegionsPt: List<RectF>,
        fillColorArgb: Int = 0xFF000000.toInt()
    ): EngineResult<RedactionOutcome>

    suspend fun undo(sessionId: SessionId): EngineResult<EditStateSnapshot>
    suspend fun redo(sessionId: SessionId): EngineResult<EditStateSnapshot>
    suspend fun commitSave(sessionId: SessionId, targetUri: Uri? = null): EngineResult<Unit>
}
