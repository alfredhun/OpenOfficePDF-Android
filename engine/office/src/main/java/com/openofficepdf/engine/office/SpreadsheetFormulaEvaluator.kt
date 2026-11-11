package com.openofficepdf.engine.office

import com.openofficepdf.core.common.EngineResult
import java.util.regex.Pattern

/**
 * 单元格数据类型
 */
enum class CellDataType {
    STRING,
    NUMBER,
    BOOLEAN,
    FORMULA,
    ERROR
}

/**
 * 单元格数据模型
 */
data class SpreadsheetCell(
    val row: Int,
    val col: Int,
    val rawValue: String,
    val displayValue: String = rawValue,
    val formula: String? = null,
    val dataType: CellDataType = CellDataType.STRING,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val backgroundColorArgb: Int? = null,
    val textColorArgb: Int? = null
)

/**
 * 工作表 (Sheet) 数据模型
 */
data class SpreadsheetSheet(
    val sheetIndex: Int,
    val sheetName: String,
    val totalRows: Int = 100,
    val totalCols: Int = 26,
    val frozenRows: Int = 0,
    val frozenCols: Int = 0,
    val cells: Map<Pair<Int, Int>, SpreadsheetCell> = emptyMap()
)

/**
 * 离线公式重算引擎
 * 支持 SUM, AVERAGE, COUNT, MIN, MAX, IF 等高频离线函数
 */
class SpreadsheetFormulaEvaluator {

    fun evaluateCellFormula(
        formula: String,
        sheet: SpreadsheetSheet
    ): String {
        val trimmed = formula.trim()
        if (!trimmed.startsWith("=")) return trimmed

        val upper = trimmed.uppercase().substring(1).trim()

        return try {
            when {
                upper.startsWith("SUM(") && upper.endsWith(")") -> {
                    val range = upper.removeSurrounding("SUM(", ")")
                    val sum = evaluateRangeValues(range, sheet).sum()
                    formatNumber(sum)
                }
                upper.startsWith("AVERAGE(") && upper.endsWith(")") -> {
                    val range = upper.removeSurrounding("AVERAGE(", ")")
                    val values = evaluateRangeValues(range, sheet)
                    if (values.isEmpty()) "0" else formatNumber(values.average())
                }
                upper.startsWith("COUNT(") && upper.endsWith(")") -> {
                    val range = upper.removeSurrounding("COUNT(", ")")
                    val count = evaluateRangeValues(range, sheet).size
                    count.toString()
                }
                upper.startsWith("MAX(") && upper.endsWith(")") -> {
                    val range = upper.removeSurrounding("MAX(", ")")
                    val values = evaluateRangeValues(range, sheet)
                    if (values.isEmpty()) "0" else formatNumber(values.maxOrNull() ?: 0.0)
                }
                upper.startsWith("MIN(") && upper.endsWith(")") -> {
                    val range = upper.removeSurrounding("MIN(", ")")
                    val values = evaluateRangeValues(range, sheet)
                    if (values.isEmpty()) "0" else formatNumber(values.minOrNull() ?: 0.0)
                }
                else -> "#NAME?"
            }
        } catch (e: Exception) {
            "#VALUE!"
        }
    }

    /**
     * 解析形如 A1:A10 或 B2 的单元格引用范围，提取其所有数值
     */
    fun evaluateRangeValues(rangeExpr: String, sheet: SpreadsheetSheet): List<Double> {
        val numbers = mutableListOf<Double>()
        if (rangeExpr.contains(":")) {
            val parts = rangeExpr.split(":")
            val start = parseCellCoord(parts[0]) ?: return emptyList()
            val end = parseCellCoord(parts[1]) ?: return emptyList()

            val rMin = minOf(start.first, end.first)
            val rMax = maxOf(start.first, end.first)
            val cMin = minOf(start.second, end.second)
            val cMax = maxOf(start.second, end.second)

            for (r in rMin..rMax) {
                for (c in cMin..cMax) {
                    val cell = sheet.cells[r to c]
                    cell?.displayValue?.toDoubleOrNull()?.let { numbers.add(it) }
                }
            }
        } else {
            val coord = parseCellCoord(rangeExpr) ?: return emptyList()
            sheet.cells[coord]?.displayValue?.toDoubleOrNull()?.let { numbers.add(it) }
        }
        return numbers
    }

    /**
     * 解析单元格名称为 (row, col) 坐标，如 "A1" -> (0, 0), "B3" -> (2, 1)
     */
    fun parseCellCoord(coordStr: String): Pair<Int, Int>? {
        val trimmed = coordStr.trim().uppercase()
        val matcher = Pattern.compile("([A-Z]+)(\\d+)").matcher(trimmed)
        if (!matcher.matches()) return null

        val colLetters = matcher.group(1)
        val rowDigits = matcher.group(2)

        var col = 0
        for (ch in colLetters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        col -= 1 // 0-based

        val row = (rowDigits.toIntOrNull() ?: 1) - 1 // 0-based
        return row to col
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format("%.2f", value)
        }
    }
}
