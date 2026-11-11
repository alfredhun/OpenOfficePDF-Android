package com.openofficepdf.engine.pdf

import android.graphics.Matrix
import android.graphics.RectF

/**
 * 严格按照任务书 5.1 节执行的三级坐标转换契约实现:
 * [PDF Raw User Space] <---> [Normalized Page Space] <---> [Compose Screen Pixels]
 * 原点: 左下角 (pt)            原点: 旋转后左上角 (pt)          原点: 视图左上角 (px)
 */
object NormalizedCoordinatesTransformer {

    /**
     * 将底层 PDF User Space 原始坐标转换为 Normalized Page Space
     * @param rawUserBounds 原始 User Space 矩形
     * @param pageHeightPt 页面高度 (pt)
     * @param rotationDeg 页面固有旋转角度 (0, 90, 180, 270)
     */
    fun userSpaceToNormalized(
        rawUserBounds: RectF,
        pageWidthPt: Float,
        pageHeightPt: Float,
        rotationDeg: Int
    ): RectF {
        val out = RectF()
        when (rotationDeg % 360) {
            0 -> {
                // 原点从左下角转为左上角: y_norm = pageHeight - y_raw_top
                out.left = rawUserBounds.left
                out.top = pageHeightPt - rawUserBounds.top
                out.right = rawUserBounds.right
                out.bottom = pageHeightPt - rawUserBounds.bottom
            }
            90 -> {
                out.left = rawUserBounds.bottom
                out.top = rawUserBounds.left
                out.right = rawUserBounds.top
                out.bottom = rawUserBounds.right
            }
            180 -> {
                out.left = pageWidthPt - rawUserBounds.right
                out.top = rawUserBounds.bottom
                out.right = pageWidthPt - rawUserBounds.left
                out.bottom = rawUserBounds.top
            }
            270 -> {
                out.left = pageHeightPt - rawUserBounds.top
                out.top = pageWidthPt - rawUserBounds.right
                out.right = pageHeightPt - rawUserBounds.bottom
                out.bottom = pageWidthPt - rawUserBounds.left
            }
        }
        // 确保 left < right, top < bottom
        out.sort()
        return out
    }

    /**
     * 将 Normalized Page Space 坐标映射到当前屏幕视口像素
     * @param normalizedBounds 归一化矩形 (pt)
     * @param zoomScale 当前手势缩放比例
     * @param scrollOffsetX 水平滚动偏移 (px)
     * @param scrollOffsetY 垂直滚动偏移 (px)
     * @param dpiScale 点到屏幕像素基础密度比例 (1 pt = 1/72 inch -> px)
     */
    fun normalizedToScreenPixels(
        normalizedBounds: RectF,
        zoomScale: Float,
        scrollOffsetX: Float,
        scrollOffsetY: Float,
        dpiScale: Float
    ): RectF {
        val totalScale = zoomScale * dpiScale
        return RectF(
            normalizedBounds.left * totalScale - scrollOffsetX,
            normalizedBounds.top * totalScale - scrollOffsetY,
            normalizedBounds.right * totalScale - scrollOffsetX,
            normalizedBounds.bottom * totalScale - scrollOffsetY
        )
    }

    /**
     * 将屏幕手势触摸像素坐标逆向转回 Normalized Page Space
     */
    fun screenPixelsToNormalized(
        screenBounds: RectF,
        zoomScale: Float,
        scrollOffsetX: Float,
        scrollOffsetY: Float,
        dpiScale: Float
    ): RectF {
        val totalScale = zoomScale * dpiScale
        return RectF(
            (screenBounds.left + scrollOffsetX) / totalScale,
            (screenBounds.top + scrollOffsetY) / totalScale,
            (screenBounds.right + scrollOffsetX) / totalScale,
            (screenBounds.bottom + scrollOffsetY) / totalScale
        )
    }
}
