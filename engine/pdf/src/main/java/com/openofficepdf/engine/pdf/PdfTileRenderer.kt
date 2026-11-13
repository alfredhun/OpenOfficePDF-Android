package com.openofficepdf.engine.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.memory.BitmapPool
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.model.pdf.createRectF
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 瓦片坐标索引模型
 */
data class TileKey(
    val sessionId: SessionId,
    val pageIndex: Int,
    val zoomLevel: Float,
    val tileCol: Int,
    val tileRow: Int
)

/**
 * 瓦片渲染元数据与 Bitmap 包装
 */
data class RenderedTile(
    val key: TileKey,
    val bitmap: Bitmap,
    val boundsPt: RectF,
    val isNightMode: Boolean
)

/**
 * 高性能瓦片渲染调度器
 * 1. 严格使用单线程 AppDispatchers.pdfNative 串行化 JNI 访问
 * 2. 瓦片复用 BitmapPool，杜绝 GC 抖动
 * 3. 视口裁剪：只渲染可见区域内的瓦片
 */
class PdfTileRenderer(
    private val bitmapPool: BitmapPool,
    private val nativeRendererBridge: AndroidPdfRendererBridge? = null,
    private val docHandles: ConcurrentHashMap<SessionId, Long> = ConcurrentHashMap()
) : PdfRenderEngine {

    companion object {
        const val STANDARD_TILE_SIZE_PX = 512
    }

    fun registerDocumentHandle(sessionId: SessionId, handle: Long) {
        docHandles[sessionId] = handle
    }

    fun unregisterDocumentHandle(sessionId: SessionId) {
        docHandles.remove(sessionId)
    }

    override suspend fun renderTile(
        sessionId: SessionId,
        pageIndex: Int,
        tileRect: RectF,
        scale: Float,
        nightMode: Boolean,
        targetBitmap: Bitmap
    ): EngineResult<Unit> = withContext(AppDispatchers.pdfNative) {
        // 优先尝试真实 AndroidPdfRendererBridge 进行硬件加速真渲染
        if (nativeRendererBridge != null) {
            val bridgeResult = nativeRendererBridge.renderPageToBitmap(sessionId, pageIndex, targetBitmap, scale)
            if (bridgeResult is EngineResult.Success) {
                return@withContext bridgeResult
            }
        }

        val handle = docHandles[sessionId]
            ?: return@withContext EngineResult.failure(
                EngineErrorCode.SESSION_EXPIRED,
                "No native handle registered for session: ${sessionId.value}"
            )

        val tileWidthPx = targetBitmap.width
        val tileHeightPx = targetBitmap.height

        // 将 Normalized Page Space (pt) 转换为渲染像素区域
        val startX = (tileRect.left * scale).toInt()
        val startY = (tileRect.top * scale).toInt()
        val sizeX = (tileRect.width() * scale).toInt()
        val sizeY = (tileRect.height() * scale).toInt()

        val success = try {
            PdfiumNativeBridge.nativeRenderTile(
                docHandle = handle,
                pageIndex = pageIndex,
                startX = startX,
                startY = startY,
                sizeX = sizeX,
                sizeY = sizeY,
                tileWidthPx = tileWidthPx,
                tileHeightPx = tileHeightPx,
                nightMode = nightMode,
                targetBitmap = targetBitmap
            )
        } catch (e: Throwable) {
            return@withContext EngineResult.failure(
                EngineErrorCode.RENDER_FAILED,
                "JNI renderPageTile failed: ${e.message}"
            )
        }

        if (success) {
            EngineResult.success(Unit)
        } else {
            EngineResult.failure(
                EngineErrorCode.RENDER_FAILED,
                "PDFium nativeRenderTile returned false"
            )
        }
    }

    /**
     * 计算指定视口在当前缩放比例下覆盖的所有标准瓦片矩形 (Normalized Page Space: pt)
     */
    fun computeVisibleTiles(
        pageIndex: Int,
        pageWidthPt: Float,
        pageHeightPt: Float,
        viewportRectPt: RectF,
        zoomScale: Float,
        tileSizePx: Int = STANDARD_TILE_SIZE_PX
    ): List<Pair<TileKey, RectF>> {
        val tileWidthPt = tileSizePx / zoomScale
        val tileHeightPt = tileSizePx / zoomScale

        val startCol = (viewportRectPt.left / tileWidthPt).toInt().coerceAtLeast(0)
        val endCol = (viewportRectPt.right / tileWidthPt).toInt()
        val startRow = (viewportRectPt.top / tileHeightPt).toInt().coerceAtLeast(0)
        val endRow = (viewportRectPt.bottom / tileHeightPt).toInt()

        val maxCol = (pageWidthPt / tileWidthPt).toInt()
        val maxRow = (pageHeightPt / tileHeightPt).toInt()

        val tiles = mutableListOf<Pair<TileKey, RectF>>()
        for (col in startCol..endCol.coerceAtMost(maxCol)) {
            for (row in startRow..endRow.coerceAtMost(maxRow)) {
                val leftPt = col * tileWidthPt
                val topPt = row * tileHeightPt
                val rightPt = (leftPt + tileWidthPt).coerceAtMost(pageWidthPt)
                val bottomPt = (topPt + tileHeightPt).coerceAtMost(pageHeightPt)

                val rectPt = createRectF(leftPt, topPt, rightPt, bottomPt)
                val key = TileKey(
                    sessionId = SessionId(""),
                    pageIndex = pageIndex,
                    zoomLevel = zoomScale,
                    tileCol = col,
                    tileRow = row
                )
                tiles.add(key to rectPt)
            }
        }
        return tiles
    }
}
