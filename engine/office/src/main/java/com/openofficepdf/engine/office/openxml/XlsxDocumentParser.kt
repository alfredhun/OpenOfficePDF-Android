package com.openofficepdf.engine.office.openxml

import android.util.Xml
import com.openofficepdf.engine.office.CellDataType
import com.openofficepdf.engine.office.SpreadsheetCell
import com.openofficepdf.engine.office.SpreadsheetSheet
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 纯离线纯 Kotlin/Java 实现的 Excel (.xlsx) 解析器 (基于 OpenXML ECMA-376)
 * 提取共享字符串表 (sharedStrings.xml) 与 工作表 (worksheets/sheet*.xml)
 */
object XlsxDocumentParser {

    fun parse(inputStream: InputStream, title: String = "工作簿.xlsx"): ParsedXlsxDocument {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            return ParsedXlsxDocument(title, emptyList())
        }

        // 校验 ZIP 魔数: PK\x03\x04
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte() || bytes[2] != 0x03.toByte() || bytes[3] != 0x04.toByte()) {
            val fallbackSheet = SpreadsheetSheet(
                sheetIndex = 0,
                sheetName = "兼容提示",
                totalRows = 1,
                totalCols = 1,
                cells = mapOf((0 to 0) to SpreadsheetCell(0, 0, "【格式不支持】该工作簿并非标准 OpenXML (.xlsx) 格式，可能为旧版 .xls 或已加密。"))
            )
            return ParsedXlsxDocument(title, listOf(fallbackSheet))
        }

        // 由于 ZipInputStream 是单向流，将 entry 内容预先按名称分类保存在内存中
        val entries = mutableMapOf<String, ByteArray>()
        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry: ZipEntry? = try { zip.nextEntry } catch (_: Exception) { null }
        while (entry != null) {
            val name = entry.name
            if (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") || name == "xl/workbook.xml") {
                entries[name] = zip.readBytes()
            }
            try {
                zip.closeEntry()
                entry = zip.nextEntry
            } catch (_: Exception) {
                break
            }
        }

        // 1. 解析共享字符串池 (Shared Strings)
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])

        // 2. 解析工作簿中的 Sheet 列表名称
        val sheetNames = parseWorkbookSheets(entries["xl/workbook.xml"])

        // 3. 解析各个 Sheet
        val parsedSheets = mutableListOf<SpreadsheetSheet>()
        val sheetEntryKeys = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted()

        sheetEntryKeys.forEachIndexed { index, sheetKey ->
            val sheetData = entries[sheetKey] ?: return@forEachIndexed
            val sheetName = sheetNames.getOrNull(index) ?: "Sheet${index + 1}"
            val parsedSheet = parseWorksheet(
                sheetIndex = index,
                sheetName = sheetName,
                bytes = sheetData,
                sharedStrings = sharedStrings
            )
            parsedSheets.add(parsedSheet)
        }

        if (parsedSheets.isEmpty()) {
            parsedSheets.add(SpreadsheetSheet(sheetIndex = 0, sheetName = "Sheet1"))
        }

        return ParsedXlsxDocument(
            title = title,
            sheets = parsedSheets
        )
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        val currentStr = StringBuilder()
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        insideT = true
                        currentStr.append(parser.nextText())
                        insideT = false
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        list.add(currentStr.toString())
                        currentStr.clear()
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseWorkbookSheets(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val names = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name")
                if (name != null) names.add(name)
            }
            eventType = parser.next()
        }
        return names
    }

    private fun parseWorksheet(
        sheetIndex: Int,
        sheetName: String,
        bytes: ByteArray,
        sharedStrings: List<String>
    ): SpreadsheetSheet {
        val cells = mutableMapOf<Pair<Int, Int>, SpreadsheetCell>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentCellRef = ""
        var currentCellType = ""
        var currentFormula: String? = null
        var maxRow = 0
        var maxCol = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentFormula = null
                        }
                        "f" -> {
                            currentFormula = "=" + parser.nextText()
                        }
                        "v" -> {
                            val rawVal = parser.nextText()
                            val (r, c) = parseCellRef(currentCellRef)
                            if (r >= 0 && c >= 0) {
                                if (r > maxRow) maxRow = r
                                if (c > maxCol) maxCol = c

                                val displayVal = if (currentCellType == "s") {
                                    val idx = rawVal.toIntOrNull()
                                    if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else rawVal
                                } else {
                                    rawVal
                                }

                                val dataType = when {
                                    currentFormula != null -> CellDataType.FORMULA
                                    currentCellType == "s" -> CellDataType.STRING
                                    rawVal.toDoubleOrNull() != null -> CellDataType.NUMBER
                                    else -> CellDataType.STRING
                                }

                                cells[r to c] = SpreadsheetCell(
                                    row = r,
                                    col = c,
                                    rawValue = displayVal,
                                    displayValue = displayVal,
                                    formula = currentFormula,
                                    dataType = dataType
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return SpreadsheetSheet(
            sheetIndex = sheetIndex,
            sheetName = sheetName,
            totalRows = maxOf(maxRow + 1, 30),
            totalCols = maxOf(maxCol + 1, 10),
            cells = cells
        )
    }

    /**
     * 将 "B3" 转为 (row=2, col=1) 零基坐标
     */
    private fun parseCellRef(ref: String): Pair<Int, Int> {
        if (ref.isEmpty()) return -1 to -1
        var col = 0
        var row = 0
        var rowStart = 0
        for (i in ref.indices) {
            val ch = ref[i]
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch.isDigit()) {
                rowStart = i
                break
            }
        }
        val rowStr = ref.substring(rowStart)
        row = (rowStr.toIntOrNull() ?: 1) - 1
        return row to (col - 1)
    }
}
