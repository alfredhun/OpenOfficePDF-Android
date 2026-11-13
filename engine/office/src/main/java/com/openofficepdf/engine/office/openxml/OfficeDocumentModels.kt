package com.openofficepdf.engine.office.openxml

import com.openofficepdf.engine.office.CellDataType
import com.openofficepdf.engine.office.SpreadsheetCell
import com.openofficepdf.engine.office.SpreadsheetSheet

/**
 * 解析后的完整 Excel 文档对象模型
 */
data class ParsedXlsxDocument(
    val title: String,
    val sheets: List<SpreadsheetSheet> = emptyList()
)

/**
 * 幻灯片图文内容块
 */
data class PptxSlideContent(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val notes: String? = null,
    val imageBytesList: List<ByteArray> = emptyList()
)

/**
 * 解析后的完整 PPT 演示文稿对象模型
 */
data class ParsedPptxDocument(
    val title: String,
    val slideWidthPt: Float = 960f,
    val slideHeightPt: Float = 540f,
    val slides: List<PptxSlideContent> = emptyList()
)
