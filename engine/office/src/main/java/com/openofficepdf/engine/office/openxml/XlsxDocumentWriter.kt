package com.openofficepdf.engine.office.openxml

import com.openofficepdf.engine.office.SpreadsheetCell
import com.openofficepdf.engine.office.SpreadsheetSheet
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 纯本地离线 Excel (.xlsx) OpenXML 写入与序列化器 (ECMA-376)
 * 支持将内存中的 SpreadsheetSheet 数据打包生成合规的 .xlsx ZIP 容器
 */
object XlsxDocumentWriter {

    fun createBlankWorkbook(sheetName: String = "Sheet1"): ParsedXlsxDocument {
        val blankSheet = SpreadsheetSheet(
            sheetIndex = 0,
            sheetName = sheetName,
            totalRows = 50,
            totalCols = 15,
            cells = emptyMap()
        )
        return ParsedXlsxDocument(
            title = "新建工作簿.xlsx",
            sheets = listOf(blankSheet)
        )
    }

    fun writeXlsxToZip(targetFile: File, document: ParsedXlsxDocument) {
        val sheets = if (document.sheets.isEmpty()) listOf(SpreadsheetSheet(0, "Sheet1")) else document.sheets
        
        // 1. 搜集所有文本以生成 sharedStrings.xml
        val stringList = mutableListOf<String>()
        val stringIndexMap = mutableMapOf<String, Int>()

        for (sheet in sheets) {
            for ((_, cell) in sheet.cells) {
                val str = cell.rawValue
                if (cell.formula == null && str.isNotEmpty() && str.toDoubleOrNull() == null) {
                    if (!stringIndexMap.containsKey(str)) {
                        stringIndexMap[str] = stringList.size
                        stringList.add(str)
                    }
                }
            }
        }

        val entries = mutableMapOf<String, ByteArray>()

        // 2. [Content_Types].xml
        entries["[Content_Types].xml"] = buildContentTypesXml(sheets.size).toByteArray(Charsets.UTF_8)

        // 3. _rels/.rels
        entries["_rels/.rels"] = buildRootRelsXml().toByteArray(Charsets.UTF_8)

        // 4. xl/_rels/workbook.xml.rels
        entries["xl/_rels/workbook.xml.rels"] = buildWorkbookRelsXml(sheets.size).toByteArray(Charsets.UTF_8)

        // 5. xl/workbook.xml
        entries["xl/workbook.xml"] = buildWorkbookXml(sheets).toByteArray(Charsets.UTF_8)

        // 6. xl/sharedStrings.xml
        entries["xl/sharedStrings.xml"] = buildSharedStringsXml(stringList).toByteArray(Charsets.UTF_8)

        // 7. xl/worksheets/sheet{i}.xml
        sheets.forEachIndexed { idx, sheet ->
            val sheetXml = buildWorksheetXml(sheet, stringIndexMap)
            entries["xl/worksheets/sheet${idx + 1}.xml"] = sheetXml.toByteArray(Charsets.UTF_8)
        }

        // 8. 写入 Zip 容器
        targetFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    private fun buildContentTypesXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sb.append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        for (i in 1..sheetCount) {
            sb.append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sb.append("""</Types>""")
        return sb.toString()
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        sb.append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        sb.append("""</Relationships>""")
        return sb.toString()
    }

    private fun buildWorkbookXml(sheets: List<SpreadsheetSheet>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheets>""")
        sheets.forEachIndexed { idx, sheet ->
            val name = escapeXml(sheet.sheetName.ifEmpty { "Sheet${idx + 1}" })
            sb.append("""<sheet name="$name" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        sb.append("""</sheets>""")
        sb.append("""</workbook>""")
        return sb.toString()
    }

    private fun buildSharedStringsXml(stringList: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${stringList.size}" uniqueCount="${stringList.size}">""")
        for (s in stringList) {
            sb.append("""<si><t>${escapeXml(s)}</t></si>""")
        }
        sb.append("""</sst>""")
        return sb.toString()
    }

    private fun buildWorksheetXml(sheet: SpreadsheetSheet, stringIndexMap: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetData>""")

        // 按行号分组
        val rowsMap = sheet.cells.entries.groupBy { it.key.first }.toSortedMap()

        for ((rowIndex, cellEntries) in rowsMap) {
            val r = rowIndex + 1
            sb.append("""<row r="$r">""")
            val sortedCols = cellEntries.sortedBy { it.key.second }
            for (entry in sortedCols) {
                val colIndex = entry.key.second
                val cell = entry.value
                val cellRef = colIndexToName(colIndex) + r

                val formula = cell.formula
                val rawVal = cell.rawValue

                if (formula != null) {
                    val formulaClean = if (formula.startsWith("=")) formula.substring(1) else formula
                    sb.append("""<c r="$cellRef"><f>${escapeXml(formulaClean)}</f><v>${escapeXml(cell.displayValue)}</v></c>""")
                } else {
                    val num = rawVal.toDoubleOrNull()
                    if (num != null) {
                        sb.append("""<c r="$cellRef"><v>$rawVal</v></c>""")
                    } else if (rawVal.isNotEmpty()) {
                        val sstIdx = stringIndexMap[rawVal] ?: 0
                        sb.append("""<c r="$cellRef" t="s"><v>$sstIdx</v></c>""")
                    }
                }
            }
            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun colIndexToName(colIndex: Int): String {
        var temp = colIndex
        val sb = StringBuilder()
        while (temp >= 0) {
            sb.insert(0, ('A'.code + (temp % 26)).toChar())
            temp = (temp / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
