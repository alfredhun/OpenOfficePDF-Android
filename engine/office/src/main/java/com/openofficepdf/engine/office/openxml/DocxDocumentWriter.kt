package com.openofficepdf.engine.office.openxml

import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 纯离线 OpenXML DOCX 增量文档序列化器
 * 保留原 zip 压缩包内的所有样式、元数据和关系文件，仅替换 word/document.xml
 */
object DocxDocumentWriter {

    fun serializeDocumentXml(doc: ParsedDocxDocument): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        for (block in doc.blocks) {
            when (block) {
                is DocxBlock.ParagraphBlock -> {
                    sb.append("<w:p>")
                    sb.append("<w:pPr>")
                    if (block.paragraph.isHeading) {
                        sb.append("<w:pStyle w:val=\"Heading${block.paragraph.headingLevel}\"/>")
                    }
                    val jcVal = when (block.paragraph.alignment) {
                        DocxAlignment.CENTER -> "center"
                        DocxAlignment.RIGHT -> "right"
                        DocxAlignment.BOTH -> "both"
                        else -> "left"
                    }
                    sb.append("<w:jc w:val=\"$jcVal\"/>")
                    sb.append("</w:pPr>")

                    for (run in block.paragraph.runs) {
                        sb.append("<w:r>")
                        sb.append("<w:rPr>")
                        if (run.isBold) sb.append("<w:b/>")
                        if (run.isItalic) sb.append("<w:i/>")
                        if (run.isUnderline) sb.append("<w:u w:val=\"single\"/>")
                        if (run.fontSizePt > 0) {
                            val halfPt = (run.fontSizePt * 2).toInt()
                            sb.append("<w:sz w:val=\"$halfPt\"/>")
                        }
                        if (!run.colorHex.isNullOrBlank()) {
                            sb.append("<w:color w:val=\"${run.colorHex.removePrefix("#")}\"/>")
                        }
                        sb.append("</w:rPr>")
                        sb.append("<w:t xml:space=\"preserve\">")
                        sb.append(escapeXml(run.text))
                        sb.append("</w:t>")
                        sb.append("</w:r>")
                    }
                    sb.append("</w:p>")
                }
                is DocxBlock.TableBlock -> {
                    sb.append("<w:tbl>")
                    sb.append("<w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/><w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/><w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/><w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/><w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/></w:tblBorders></w:tblPr>")
                    for (row in block.table.rows) {
                        sb.append("<w:tr>")
                        for (cell in row) {
                            sb.append("<w:tc>")
                            for (p in cell.paragraphs) {
                                sb.append("<w:p>")
                                for (run in p.runs) {
                                    sb.append("<w:r><w:t xml:space=\"preserve\">${escapeXml(run.text)}</w:t></w:r>")
                                }
                                sb.append("</w:p>")
                            }
                            if (cell.paragraphs.isEmpty()) {
                                sb.append("<w:p/>")
                            }
                            sb.append("</w:tc>")
                        }
                        sb.append("</w:tr>")
                    }
                    sb.append("</w:tbl>")
                }
            }
        }

        sb.append("<w:sectPr/>")
        sb.append("</w:body>")
        sb.append("</w:document>")
        return sb.toString()
    }

    /**
     * 将编辑后的 DOM 内容注入并写回物理 stagingFile
     */
    fun writeDocxToZip(originalStagingFile: File, doc: ParsedDocxDocument) {
        val newXml = serializeDocumentXml(doc)
        val tempOut = File(originalStagingFile.parentFile, "${originalStagingFile.name}.tmp")

        var replacedDocumentXml = false
        val originalBytes = originalStagingFile.readBytes()

        // 校验原文件是否是有效 Zip
        val isZip = originalBytes.size >= 4 &&
                originalBytes[0] == 0x50.toByte() &&
                originalBytes[1] == 0x4B.toByte() &&
                originalBytes[2] == 0x03.toByte() &&
                originalBytes[3] == 0x04.toByte()

        if (isZip) {
            ZipInputStream(ByteArrayInputStream(originalBytes)).use { zis ->
                ZipOutputStream(FileOutputStream(tempOut)).use { zos ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            // 替换内容
                            val newEntry = ZipEntry("word/document.xml")
                            zos.putNextEntry(newEntry)
                            zos.write(newXml.toByteArray(StandardCharsets.UTF_8))
                            zos.closeEntry()
                            replacedDocumentXml = true
                        } else {
                            // 保留原有其他 entry
                            val copyEntry = ZipEntry(entry.name)
                            zos.putNextEntry(copyEntry)
                            zis.copyTo(zos)
                            zos.closeEntry()
                        }
                        entry = zis.nextEntry
                    }
                    if (!replacedDocumentXml) {
                        val newEntry = ZipEntry("word/document.xml")
                        zos.putNextEntry(newEntry)
                        zos.write(newXml.toByteArray(StandardCharsets.UTF_8))
                        zos.closeEntry()
                    }
                }
            }
        } else {
            // 原文件非 zip (例如空白文件或直接创建)，新建最小合规 openxml docx
            ZipOutputStream(FileOutputStream(tempOut)).use { zos ->
                // [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".trimIndent().toByteArray(StandardCharsets.UTF_8)
                )
                zos.closeEntry()

                // _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".trimIndent().toByteArray(StandardCharsets.UTF_8)
                )
                zos.closeEntry()

                // word/document.xml
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(newXml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }

        // 原子覆盖原 staging 文件
        if (tempOut.exists()) {
            originalStagingFile.delete()
            tempOut.renameTo(originalStagingFile)
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
