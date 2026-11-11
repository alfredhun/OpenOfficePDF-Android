package com.openofficepdf.core.model.pdf

import android.graphics.Matrix
import android.graphics.RectF

/**
 * 所有 bounds 与 transform 强制处于 Normalized Page Space (任务书 5.1 节)
 * 单位: pt (1 pt = 1/72 inch)
 * 原点: 考虑页面固有旋转与 CropBox 后的页面左上角
 * X轴: 向右递增
 * Y轴: 向下递增，与手势缩放无关
 */
sealed interface PdfObjectModel {
    val objectId: String          // 格式: "p{pageIndex}_obj{nativeHandleHash}"
    val pageIndex: Int
    val bounds: RectF
    val transform: Matrix
    val zIndex: Int
    val editCapability: EditCapability
}

enum class EditCapability {
    FULL_IN_PLACE,                // 支持原位无损修改 (二级策略)
    REQUIRES_FONT_FALLBACK,       // 原字体为子集且缺字，需触发开源中文字体回退
    OVERLAY_ONLY,                 // 复杂编码或转曲文本，仅支持覆盖式编辑 (一级保底)
    READ_ONLY_PROTECTED           // 受数字签名或加密权限保护，禁止修改
}

data class PdfTextObject(
    override val objectId: String,
    override val pageIndex: Int,
    override val bounds: RectF,
    override val transform: Matrix,
    override val zIndex: Int,
    override val editCapability: EditCapability,
    val text: String,
    val fontInfo: PdfFontDescriptor,
    val fontSizePt: Float,
    val fillColorArgb: Int,
    val charBoxes: List<RectF>    // 逐字符归一化边界框，用于精准光标定位
) : PdfObjectModel

data class PdfImageObject(
    override val objectId: String,
    override val pageIndex: Int,
    override val bounds: RectF,
    override val transform: Matrix,
    override val zIndex: Int,
    override val editCapability: EditCapability,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val colorSpace: String,
    val hasSoftMask: Boolean
) : PdfObjectModel

data class PdfPathObject(
    override val objectId: String,
    override val pageIndex: Int,
    override val bounds: RectF,
    override val transform: Matrix,
    override val zIndex: Int,
    override val editCapability: EditCapability,
    val strokeColorArgb: Int?,
    val fillColorArgb: Int?,
    val strokeWidthPt: Float
) : PdfObjectModel

data class PdfFontDescriptor(
    val fontName: String,
    val isEmbedded: Boolean,
    val isSubset: Boolean,
    val encodingType: String,     // Identity-H, WinAnsiEncoding, ToUnicode 等
    val canEncodeNewChars: Boolean
)
