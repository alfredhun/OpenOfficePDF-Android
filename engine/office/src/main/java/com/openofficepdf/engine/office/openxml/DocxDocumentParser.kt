package com.openofficepdf.engine.office.openxml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 纯离线纯 Kotlin/Java 实现的 Word (.docx) 解析器 (基于 OpenXML ECMA-376)
 * 无外部重量级依赖，100% 离线、快速、安全
 */
object DocxDocumentParser {

    fun parse(inputStream: InputStream, title: String = "文档.docx"): ParsedDocxDocument {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            return ParsedDocxDocument(title, emptyList(), 0)
        }

        // 校验 ZIP 魔数: PK\x03\x04
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte() || bytes[2] != 0x03.toByte() || bytes[3] != 0x04.toByte()) {
            return ParsedDocxDocument(
                title = title,
                blocks = listOf(
                    DocxBlock.ParagraphBlock(
                        DocxParagraph(
                            runs = listOf(DocxRun(text = "【格式不支持】该文档并非标准 OpenXML (.docx) 格式，可能为旧版二进制 .doc 或加密文档。")),
                            isHeading = false
                        )
                    )
                ),
                totalWords = 0
            )
        }

        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry: ZipEntry? = try { zip.nextEntry } catch (_: Exception) { null }
        val blocks = mutableListOf<DocxBlock>()

        while (entry != null) {
            if (entry.name == "word/document.xml") {
                parseDocumentXml(zip, blocks)
                break
            }
            try {
                zip.closeEntry()
                entry = zip.nextEntry
            } catch (_: Exception) {
                break
            }
        }

        val totalWords = blocks.sumOf { block ->
            when (block) {
                is DocxBlock.ParagraphBlock -> block.paragraph.fullText.length
                is DocxBlock.TableBlock -> block.table.rows.sumOf { row ->
                    row.sumOf { cell -> cell.text.length }
                }
            }
        }

        return ParsedDocxDocument(
            title = title,
            blocks = blocks,
            totalWords = totalWords
        )
    }

    private fun parseDocumentXml(stream: InputStream, outBlocks: MutableList<DocxBlock>) {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "p" -> {
                        val p = parseParagraph(parser)
                        if (p.runs.isNotEmpty() && p.fullText.isNotBlank()) {
                            outBlocks.add(DocxBlock.ParagraphBlock(p))
                        }
                    }
                    "tbl" -> {
                        val table = parseTable(parser)
                        if (table.rows.isNotEmpty()) {
                            outBlocks.add(DocxBlock.TableBlock(table))
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseParagraph(parser: XmlPullParser): DocxParagraph {
        val runs = mutableListOf<DocxRun>()
        var alignment = DocxAlignment.LEFT
        var isHeading = false
        var headingLevel = 0

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "jc" -> {
                            val valAttr = parser.getAttributeValue(null, "val")
                            alignment = when (valAttr) {
                                "center" -> DocxAlignment.CENTER
                                "right" -> DocxAlignment.RIGHT
                                "both" -> DocxAlignment.BOTH
                                else -> DocxAlignment.LEFT
                            }
                        }
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.startsWith("Heading") || styleVal.contains("标题")) {
                                isHeading = true
                                headingLevel = (styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1).coerceIn(1, 6)
                            }
                        }
                        "r" -> {
                            runs.add(parseRun(parser))
                            depth-- // parseRun 消耗了对应的 END_TAG
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        return DocxParagraph(
            runs = runs,
            alignment = alignment,
            isHeading = isHeading,
            headingLevel = headingLevel
        )
    }

    private fun parseRun(parser: XmlPullParser): DocxRun {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var fontSizePt = 12f
        var colorHex: String? = null
        val textBuilder = StringBuilder()

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "b" -> isBold = true
                        "i" -> isItalic = true
                        "u" -> isUnderline = true
                        "sz" -> {
                            val halfPts = parser.getAttributeValue(null, "val")?.toFloatOrNull()
                            if (halfPts != null) fontSizePt = halfPts / 2.0f
                        }
                        "color" -> {
                            colorHex = parser.getAttributeValue(null, "val")
                        }
                        "t" -> {
                            textBuilder.append(parser.nextText())
                            depth-- // nextText 自动消耗了当前标签的 END_TAG
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        return DocxRun(
            text = textBuilder.toString(),
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            fontSizePt = fontSizePt,
            colorHex = colorHex
        )
    }

    private fun parseTable(parser: XmlPullParser): DocxTable {
        val rows = mutableListOf<List<DocxTableCell>>()
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "tr") {
                        rows.add(parseTableRow(parser))
                        depth-- // parseTableRow 消耗了 tr 的 END_TAG
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        return DocxTable(rows = rows)
    }

    private fun parseTableRow(parser: XmlPullParser): List<DocxTableCell> {
        val cells = mutableListOf<DocxTableCell>()
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "tc") {
                        cells.add(parseTableCell(parser))
                        depth-- // parseTableCell 消耗了 tc 的 END_TAG
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        return cells
    }

    private fun parseTableCell(parser: XmlPullParser): DocxTableCell {
        val paragraphs = mutableListOf<DocxParagraph>()
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "p") {
                        val p = parseParagraph(parser)
                        if (p.runs.isNotEmpty()) paragraphs.add(p)
                        depth-- // parseParagraph 消耗了 p 的 END_TAG
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        return DocxTableCell(paragraphs = paragraphs)
    }
}
