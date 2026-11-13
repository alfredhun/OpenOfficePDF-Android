package com.openofficepdf.engine.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.engine.office.openxml.DocxDocumentParser
import com.openofficepdf.engine.office.openxml.XlsxDocumentParser
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 真实离线跨格式转换管线
 * 1. 100% 离线、纯原生无网络
 * 2. DOCX -> PDF: 基于 OpenXML 解析出的段落、标题与表格，利用 PdfDocument 绘制排版分页生成标准 PDF
 * 3. XLSX -> PDF: 将表格行列网格绘制为分页 PDF
 * 4. PDF/IMG -> DOCX: 将提取的纯文本与段落重新打包为标准 OpenXML .docx (包含 [Content_Types].xml, _rels, word/document.xml)
 */
class RealDocumentConverter(
    private val context: Context
) {
    private val docxParser = DocxDocumentParser()
    private val xlsxParser = XlsxDocumentParser()

    /**
     * 将 Word (DOCX) 转换为 PDF
     */
    suspend fun convertDocxToPdf(sourceUri: Uri, outputUri: Uri): EngineResult<Int> = withContext(AppDispatchers.io) {
        try {
            val docxModel = docxParser.parseDocx(context, sourceUri)
                ?: return@withContext EngineResult.failure(EngineErrorCode.FILE_READ_FAILED, "无法解析 Word 文档")

            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 宽度点数
            val pageHeight = 842 // A4 高度点数
            val margin = 50f
            var pageNum = 1

            var currentY = margin
            var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var currentPage = pdfDocument.startPage(currentPageInfo)
            var canvas = currentPage.canvas

            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
                textSize = 14f
            }

            fun checkPageBreak(requiredHeight: Float) {
                if (currentY + requiredHeight > pageHeight - margin) {
                    pdfDocument.finishPage(currentPage)
                    pageNum++
                    currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    currentPage = pdfDocument.startPage(currentPageInfo)
                    canvas = currentPage.canvas
                    currentY = margin
                }
            }

            // 绘制段落与内容
            for (para in docxModel.paragraphs) {
                paint.textSize = when (para.styleName) {
                    "Title" -> 22f
                    "Heading 1" -> 18f
                    "Heading 2" -> 16f
                    else -> 13f
                }
                paint.isFakeBoldText = para.runs.any { it.bold } || para.styleName?.startsWith("Heading") == true

                val text = para.text.ifBlank { " " }
                checkPageBreak(paint.textSize + 12f)
                canvas.drawText(text, margin, currentY + paint.textSize, paint)
                currentY += paint.textSize + 10f
            }

            // 绘制表格
            for (table in docxModel.tables) {
                val rowHeight = 24f
                val colWidth = (pageWidth - margin * 2) / 4f // 默认四等分

                for (row in table.rows) {
                    checkPageBreak(rowHeight + 4f)
                    for ((cIdx, cell) in row.cells.withIndex()) {
                        val cellLeft = margin + cIdx * colWidth
                        val cellTop = currentY
                        val cellRight = cellLeft + colWidth
                        val cellBottom = cellTop + rowHeight

                        val borderPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            color = Color.LTGRAY
                            strokeWidth = 1f
                        }
                        canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, borderPaint)

                        paint.textSize = 11f
                        paint.isFakeBoldText = false
                        val cellText = cell.text.take(15)
                        canvas.drawText(cellText, cellLeft + 4f, cellTop + 16f, paint)
                    }
                    currentY += rowHeight
                }
                currentY += 12f
            }

            pdfDocument.finishPage(currentPage)

            context.contentResolver.openOutputStream(outputUri)?.use { outStream ->
                pdfDocument.writeTo(outStream)
            } ?: return@withContext EngineResult.failure(EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED, "无法写入目标 PDF 文件")

            pdfDocument.close()
            EngineResult.success(pageNum)
        } catch (e: Exception) {
            EngineResult.failure(EngineErrorCode.UNKNOWN_ENGINE_ERROR, "DOCX 转 PDF 失败: ${e.message}")
        }
    }

    /**
     * 将 Excel (XLSX) 转换为 PDF
     */
    suspend fun convertXlsxToPdf(sourceUri: Uri, outputUri: Uri): EngineResult<Int> = withContext(AppDispatchers.io) {
        try {
            val workbook = xlsxParser.parseXlsx(context, sourceUri)
                ?: return@withContext EngineResult.failure(EngineErrorCode.FILE_READ_FAILED, "无法解析 Excel 工作簿")

            val pdfDocument = PdfDocument()
            val pageWidth = 842 // A4 横向
            val pageHeight = 595
            val margin = 40f
            var pageNum = 1

            val sheet = workbook.sheets.firstOrNull()
            val rows = sheet?.rows ?: emptyList()

            var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var currentPage = pdfDocument.startPage(currentPageInfo)
            var canvas = currentPage.canvas
            var currentY = margin

            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                color = Color.BLACK
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

            val colCount = 8
            val colWidth = (pageWidth - margin * 2) / colCount
            val rowHeight = 22f

            for (row in rows) {
                if (currentY + rowHeight > pageHeight - margin) {
                    pdfDocument.finishPage(currentPage)
                    pageNum++
                    currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    currentPage = pdfDocument.startPage(currentPageInfo)
                    canvas = currentPage.canvas
                    currentY = margin
                }

                for (cIdx in 0 until colCount) {
                    val cell = row.cells.getOrNull(cIdx)
                    val left = margin + cIdx * colWidth
                    val top = currentY
                    canvas.drawRect(left, top, left + colWidth, top + rowHeight, linePaint)
                    if (cell != null && cell.displayValue.isNotBlank()) {
                        canvas.drawText(cell.displayValue.take(12), left + 4f, top + 15f, textPaint)
                    }
                }
                currentY += rowHeight
            }

            pdfDocument.finishPage(currentPage)

            context.contentResolver.openOutputStream(outputUri)?.use { outStream ->
                pdfDocument.writeTo(outStream)
            } ?: return@withContext EngineResult.failure(EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED, "无法写入目标 PDF")

            pdfDocument.close()
            EngineResult.success(pageNum)
        } catch (e: Exception) {
            EngineResult.failure(EngineErrorCode.UNKNOWN_ENGINE_ERROR, "XLSX 转 PDF 失败: ${e.message}")
        }
    }

    /**
     * 将文本段落逆向生成标准 OpenXML .docx 文件 (包含必需的 ZIP 骨架结构)
     */
    suspend fun createDocxFromTextLines(lines: List<String>, outputUri: Uri): EngineResult<Unit> = withContext(AppDispatchers.io) {
        try {
            val pXmlBuilder = StringBuilder()
            for (line in lines) {
                val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                pXmlBuilder.append("<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>")
            }

            val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $pXmlBuilder
  </w:body>
</w:document>"""

            val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

            val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                ZipOutputStream(out).use { zos ->
                    // 1. [Content_Types].xml
                    zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                    zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. _rels/.rels
                    zos.putNextEntry(ZipEntry("_rels/.rels"))
                    zos.write(relsXml.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 3. word/document.xml
                    zos.putNextEntry(ZipEntry("word/document.xml"))
                    zos.write(docXml.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            } ?: return@withContext EngineResult.failure(EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED, "无法写入目标 DOCX")

            EngineResult.success(Unit)
        } catch (e: Exception) {
            EngineResult.failure(EngineErrorCode.UNKNOWN_ENGINE_ERROR, "生成 DOCX 失败: ${e.message}")
        }
    }
}
