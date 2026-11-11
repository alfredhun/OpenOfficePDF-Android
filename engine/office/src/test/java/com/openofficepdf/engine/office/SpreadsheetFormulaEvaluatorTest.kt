package com.openofficepdf.engine.office

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpreadsheetFormulaEvaluatorTest {

    private lateinit var evaluator: SpreadsheetFormulaEvaluator
    private lateinit var testSheet: SpreadsheetSheet

    @Before
    fun setUp() {
        evaluator = SpreadsheetFormulaEvaluator()

        val cells = mutableMapOf<Pair<Int, Int>, SpreadsheetCell>()
        // A1=10, A2=20, A3=30
        cells[0 to 0] = SpreadsheetCell(0, 0, "10", dataType = CellDataType.NUMBER)
        cells[1 to 0] = SpreadsheetCell(1, 0, "20", dataType = CellDataType.NUMBER)
        cells[2 to 0] = SpreadsheetCell(2, 0, "30", dataType = CellDataType.NUMBER)

        // B1=5, B2=15
        cells[0 to 1] = SpreadsheetCell(0, 1, "5", dataType = CellDataType.NUMBER)
        cells[1 to 1] = SpreadsheetCell(1, 1, "15", dataType = CellDataType.NUMBER)

        testSheet = SpreadsheetSheet(
            sheetIndex = 0,
            sheetName = "TestSheet",
            cells = cells
        )
    }

    @Test
    fun testParseCellCoord() {
        assertEquals(0 to 0, evaluator.parseCellCoord("A1"))
        assertEquals(2 to 1, evaluator.parseCellCoord("B3"))
        assertEquals(9 to 25, evaluator.parseCellCoord("Z10"))
        assertNull(evaluator.parseCellCoord("INVALID"))
    }

    @Test
    fun testEvaluateSumFormula() {
        val resultA = evaluator.evaluateCellFormula("=SUM(A1:A3)", testSheet)
        assertEquals("60", resultA)

        val resultB = evaluator.evaluateCellFormula("=sum(b1:b2)", testSheet)
        assertEquals("20", resultB)
    }

    @Test
    fun testEvaluateAverageFormula() {
        val result = evaluator.evaluateCellFormula("=AVERAGE(A1:A3)", testSheet)
        assertEquals("20", result)
    }

    @Test
    fun testEvaluateCountFormula() {
        val result = evaluator.evaluateCellFormula("=COUNT(A1:A3)", testSheet)
        assertEquals("3", result)
    }

    @Test
    fun testEvaluateMinMaxFormula() {
        val maxRes = evaluator.evaluateCellFormula("=MAX(A1:A3)", testSheet)
        assertEquals("30", maxRes)

        val minRes = evaluator.evaluateCellFormula("=MIN(A1:A3)", testSheet)
        assertEquals("10", minRes)
    }

    @Test
    fun testUnknownFormulaReturnsNameError() {
        val res = evaluator.evaluateCellFormula("=UNKNOWN_FUNC(A1)", testSheet)
        assertEquals("#NAME?", res)
    }
}
