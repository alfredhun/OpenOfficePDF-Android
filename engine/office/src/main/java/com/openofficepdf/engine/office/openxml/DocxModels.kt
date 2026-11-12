package com.openofficepdf.engine.office.openxml

/**
 * Word 段落对齐方式
 */
enum class DocxAlignment {
    LEFT,
    CENTER,
    RIGHT,
    BOTH
}

/**
 * Word 文本运行块 (Run)
 */
data class DocxRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontSizePt: Float = 12f,
    val colorHex: String? = null
)

/**
 * Word 段落 (Paragraph)
 */
data class DocxParagraph(
    val runs: List<DocxRun> = emptyList(),
    val alignment: DocxAlignment = DocxAlignment.LEFT,
    val isHeading: Boolean = false,
    val headingLevel: Int = 0
) {
    val fullText: String
        get() = runs.joinToString("") { it.text }
}

/**
 * Word 表格单元格
 */
data class DocxTableCell(
    val paragraphs: List<DocxParagraph> = emptyList()
) {
    val text: String
        get() = paragraphs.joinToString("\n") { it.fullText }
}

/**
 * Word 表格
 */
data class DocxTable(
    val rows: List<List<DocxTableCell>> = emptyList()
)

/**
 * Word 文档元素 (段落或表格)
 */
sealed class DocxBlock {
    data class ParagraphBlock(val paragraph: DocxParagraph) : DocxBlock()
    data class TableBlock(val table: DocxTable) : DocxBlock()
}

/**
 * 解析后的完整 Word 文档对象模型
 */
data class ParsedDocxDocument(
    val title: String,
    val blocks: List<DocxBlock> = emptyList(),
    val totalWords: Int = 0
)
