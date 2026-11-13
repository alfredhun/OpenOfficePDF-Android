package com.openofficepdf.engine.office

import android.graphics.Path
import android.graphics.RectF

/**
 * 幻灯片墨迹批注模型
 */
data class SlideInkStroke(
    val strokeId: String,
    val points: List<Pair<Float, Float>>, // 归一化百分比坐标 (0.0f ~ 1.0f)
    val colorArgb: Int = 0xFFFF0000.toInt(), // 默认荧光红
    val strokeWidthDp: Float = 3.0f
)

/**
 * 单页幻灯片模型
 */
data class SlidePage(
    val slideIndex: Int,
    val title: String = "幻灯片 ${slideIndex + 1}",
    val notes: String? = null,
    val widthPt: Float = 960f,  // 16:9 标准尺寸
    val heightPt: Float = 540f,
    val strokes: List<SlideInkStroke> = emptyList(),
    val imageBytesList: List<ByteArray> = emptyList()
)

/**
 * 演示文稿文稿容器
 */
data class PresentationDocument(
    val title: String,
    val totalSlides: Int,
    val slides: List<SlidePage>
)
