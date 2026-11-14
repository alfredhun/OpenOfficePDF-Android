package com.openofficepdf.engine.office.openxml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
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

        // 1. 检查是否为旧版二进制 .doc (OLE2 Compound File Header: 0xD0, 0xCF, 0x11, 0xE0)
        if (bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) {
            val extractedDocText = extractBinaryDocText(bytes)
            return if (extractedDocText.isNotBlank()) {
                val paragraphs = extractedDocText.lines().filter { it.isNotBlank() }.map { line ->
                    DocxBlock.ParagraphBlock(
                        DocxParagraph(
                            runs = listOf(DocxRun(text = line)),
                            isHeading = false
                        )
                    )
                }
                ParsedDocxDocument(
                    title = title,
                    blocks = listOf(
                        DocxBlock.ParagraphBlock(
                            DocxParagraph(
                                runs = listOf(DocxRun(text = "【旧版 Word .doc 二进制兼容模式】", isBold = true)),
                                isHeading = true,
                                headingLevel = 2
                            )
                        )
                    ) + paragraphs,
                    totalWords = extractedDocText.length
                )
            } else {
                ParsedDocxDocument(
                    title = title,
                    blocks = listOf(
                        DocxBlock.ParagraphBlock(
                            DocxParagraph(
                                runs = listOf(DocxRun(text = "【旧版 Word 97-2003 .doc 文档】该文件为老旧二进制复合文档，未检测到有效正文流。")),
                                isHeading = false
                            )
                        )
                    ),
                    totalWords = 0
                )
            }
        }

        // 2. 校验标准 ZIP 魔数: PK\x03\x04
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte() || bytes[2] != 0x03.toByte() || bytes[3] != 0x04.toByte()) {
            return ParsedDocxDocument(
                title = title,
                blocks = listOf(
                    DocxBlock.ParagraphBlock(
                        DocxParagraph(
                            runs = listOf(DocxRun(text = "【格式不支持】该文档并非有效 Word (.docx/.doc) 格式，或已损坏受密。")),
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
        var foundHeader: String? = null
        var foundFooter: String? = null

        while (entry != null) {
            val name = entry.name
            if (name == "word/document.xml") {
                parseDocumentXml(zip, blocks)
            } else if (name.startsWith("word/header") && name.endsWith(".xml")) {
                val headerBlocks = mutableListOf<DocxBlock>()
                parseDocumentXml(zip, headerBlocks)
                val text = headerBlocks.filterIsInstance<DocxBlock.ParagraphBlock>()
                    .map { it.paragraph.fullText.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                if (text.isNotBlank() && foundHeader == null) {
                    foundHeader = text
                }
            } else if (name.startsWith("word/footer") && name.endsWith(".xml")) {
                val footerBlocks = mutableListOf<DocxBlock>()
                parseDocumentXml(zip, footerBlocks)
                val text = footerBlocks.filterIsInstance<DocxBlock.ParagraphBlock>()
                    .map { it.paragraph.fullText.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                if (text.isNotBlank() && foundFooter == null) {
                    foundFooter = text
                }
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
            totalWords = totalWords,
            headerText = foundHeader,
            footerText = foundFooter
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

    /**
     * 极轻量离线提取老旧 Word 97-2003 二进制 .doc (OLE2 Compound Document) 的文本内容
     * 通过扫描 WordDocument 核心流中的连续可打印字符或 UTF-16LE 宽字符
     */
    private fun extractBinaryDocText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 512 // 跳过 OLE2 头部 512 字节
        val len = bytes.size

        while (i < len - 1) {
            // 尝试以 UTF-16LE 扫描双字节
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val charCode = (b1 shl 8) or b0

            if (charCode in 0x20..0x7E || charCode in 0x4E00..0x9FA5 || charCode == 0x0A || charCode == 0x0D) {
                if (charCode == 0x0D) {
                    sb.append('\n')
                } else if (charCode != 0x0A) {
                    sb.append(charCode.toChar())
                }
                i += 2
            } else {
                // 尝试单字节 ASCII / ANSI 字符
                val b = bytes[i].toInt() and 0xFF
                if (b in 0x20..0x7E || b in 0xA1..0xFE) {
                    sb.append(b.toChar())
                } else if (b == 0x0D) {
                    sb.append('\n')
                }
                i++
            }
        }

        // 清理杂质与连续多余换行
        val raw = sb.toString().replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        return raw.split('\n')
            .map { it.trim() }
            .filter { it.length >= 2 }
            .joinToString("\n")
    }
}
