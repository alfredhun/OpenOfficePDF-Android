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

    val id: String get() = objectId
    val boundsPt: RectF get() = bounds
    val capability: EditCapability get() = editCapability
}

enum class EditCapability {
    FULL_IN_PLACE,                // 支持原位无损修改 (二级策略)
    REQUIRES_FONT_FALLBACK,       // 原字体为子集且缺字，需触发开源中文字体回退
    OVERLAY_ONLY,                 // 复杂编码或转曲文本，仅支持覆盖式编辑 (一级保底)
    READ_ONLY_PROTECTED,          // 受数字签名或加密权限保护，禁止修改
    IN_PLACE_TEXT,
    OVERLAY_TEXT,
    OVERLAY_MASK
}

data class PdfTextObject(
    override val objectId: String,
    override val pageIndex: Int,
    override val bounds: RectF,
    override val transform: Matrix = Matrix(),
    override val zIndex: Int = 0,
    override val editCapability: EditCapability = EditCapability.FULL_IN_PLACE,
    val text: String,
    val fontInfo: PdfFontDescriptor = PdfFontDescriptor("SimSun", isEmbedded = true, isSubset = false, encodingType = "Identity-H", canEncodeNewChars = true),
    val fontSizePt: Float = 12f,
    val fillColorArgb: Int = 0xFF000000.toInt(),
    val charBoxes: List<RectF> = emptyList(),
    val fontName: String = fontInfo.fontName,
    val colorArgb: Int = fillColorArgb,
    val charSpacingPt: Float = 0f,
    val wordSpacingPt: Float = 0f
) : PdfObjectModel {
    override val id: String get() = objectId
    override val boundsPt: RectF get() = bounds
    override val capability: EditCapability get() = editCapability

    constructor(
        id: String,
        pageIndex: Int,
        boundsPt: RectF,
        transform: Matrix = Matrix(),
        text: String,
        fontSizePt: Float,
        fontName: String,
        colorArgb: Int,
        charSpacingPt: Float = 0f,
        wordSpacingPt: Float = 0f,
        capability: EditCapability = EditCapability.FULL_IN_PLACE,
        zIndex: Int = 0
    ) : this(
        objectId = id,
        pageIndex = pageIndex,
        bounds = boundsPt,
        transform = transform,
        zIndex = zIndex,
        editCapability = capability,
        text = text,
        fontInfo = PdfFontDescriptor(fontName, isEmbedded = true, isSubset = false, encodingType = "Identity-H", canEncodeNewChars = true),
        fontSizePt = fontSizePt,
        fillColorArgb = colorArgb,
        charBoxes = emptyList(),
        fontName = fontName,
        colorArgb = colorArgb,
        charSpacingPt = charSpacingPt,
        wordSpacingPt = wordSpacingPt
    )
}

data class PdfImageObject(
    override val objectId: String,
    override val pageIndex: Int,
    override val bounds: RectF,
    override val transform: Matrix = Matrix(),
    override val zIndex: Int = 0,
    override val editCapability: EditCapability = EditCapability.FULL_IN_PLACE,
    val pixelWidth: Int = 0,
    val pixelHeight: Int = 0,
    val colorSpace: String = "DeviceRGB",
    val hasSoftMask: Boolean = false
) : PdfObjectModel {
    override val id: String get() = objectId
    override val boundsPt: RectF get() = bounds
    override val capability: EditCapability get() = editCapability
}

data class PdfPathObject(
    override val objectId: String,
    override val pageIndex: Int,
    override val bounds: RectF,
    override val transform: Matrix = Matrix(),
    override val zIndex: Int = 0,
    override val editCapability: EditCapability = EditCapability.OVERLAY_MASK,
    val strokeColorArgb: Int? = null,
    val fillColorArgb: Int? = null,
    val strokeWidthPt: Float = 0f,
    val svgPathData: String = ""
) : PdfObjectModel {
    override val id: String get() = objectId
    override val boundsPt: RectF get() = bounds
    override val capability: EditCapability get() = editCapability

    constructor(
        id: String,
        pageIndex: Int,
        boundsPt: RectF,
        transform: Matrix = Matrix(),
        fillColorArgb: Int? = null,
        strokeColorArgb: Int? = null,
        strokeWidthPt: Float = 0f,
        svgPathData: String = "",
        capability: EditCapability = EditCapability.OVERLAY_MASK,
        zIndex: Int = 0
    ) : this(
        objectId = id,
        pageIndex = pageIndex,
        bounds = boundsPt,
        transform = transform,
        zIndex = zIndex,
        editCapability = capability,
        strokeColorArgb = strokeColorArgb,
        fillColorArgb = fillColorArgb,
        strokeWidthPt = strokeWidthPt,
        svgPathData = svgPathData
    )
}

data class PdfFontDescriptor(
    val fontName: String,
    val isEmbedded: Boolean,
    val isSubset: Boolean,
    val encodingType: String,     // Identity-H, WinAnsiEncoding, ToUnicode 等
    val canEncodeNewChars: Boolean
)
