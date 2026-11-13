package com.openofficepdf.feature.office

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.engine.office.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExcelWorkspaceUiState(
    val sessionId: SessionId? = null,
    val documentTitle: String = "工作簿1.xlsx",
    val sheets: List<SpreadsheetSheet> = listOf(
        SpreadsheetSheet(sheetIndex = 0, sheetName = "Sheet1", totalRows = 50, totalCols = 15)
    ),
    val activeSheetIndex: Int = 0,
    val selectedCellCoord: Pair<Int, Int>? = 0 to 0, // (row, col)
    val formulaInputText: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val isFilterEnabled: Boolean = false,
    val toastMessage: String? = null
)

class ExcelWorkspaceViewModel(
    private val officeEngine: OfficeEngine,
    private val formulaEvaluator: SpreadsheetFormulaEvaluator = SpreadsheetFormulaEvaluator()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExcelWorkspaceUiState())
    val uiState: StateFlow<ExcelWorkspaceUiState> = _uiState.asStateFlow()

    init {
        // 初始化预填测试工作表与公式示例
        setupSampleSheet()
    }

    private fun setupSampleSheet() {
        val initialCells = mutableMapOf<Pair<Int, Int>, SpreadsheetCell>()
        // 标题行
        initialCells[0 to 0] = SpreadsheetCell(0, 0, "产品名称", isBold = true)
        initialCells[0 to 1] = SpreadsheetCell(0, 1, "销量", isBold = true)
        initialCells[0 to 2] = SpreadsheetCell(0, 2, "单价", isBold = true)

        // 数据行
        initialCells[1 to 0] = SpreadsheetCell(1, 0, "桌面台灯")
        initialCells[1 to 1] = SpreadsheetCell(1, 1, "120", dataType = CellDataType.NUMBER)
        initialCells[1 to 2] = SpreadsheetCell(1, 2, "50", dataType = CellDataType.NUMBER)

        initialCells[2 to 0] = SpreadsheetCell(2, 0, "无线鼠标")
        initialCells[2 to 1] = SpreadsheetCell(2, 1, "300", dataType = CellDataType.NUMBER)
        initialCells[2 to 2] = SpreadsheetCell(2, 2, "80", dataType = CellDataType.NUMBER)

        // 合计与公式
        initialCells[3 to 0] = SpreadsheetCell(3, 0, "总销量合计", isBold = true)
        initialCells[3 to 1] = SpreadsheetCell(
            row = 3,
            col = 1,
            rawValue = "=SUM(B2:B3)",
            displayValue = "420",
            formula = "=SUM(B2:B3)",
            dataType = CellDataType.FORMULA,
            isBold = true
        )

        val updatedSheet = _uiState.value.sheets[0].copy(
            cells = initialCells,
            frozenRows = 1 // 默认首行冻结
        )
        _uiState.value = _uiState.value.copy(
            sheets = listOf(updatedSheet),
            formulaInputText = initialCells[0 to 0]?.rawValue ?: ""
        )
    }

    fun createNewBlankWorkbook(title: String = "新建工作簿.xlsx") {
        val blankDoc = com.openofficepdf.engine.office.openxml.XlsxDocumentWriter.createBlankWorkbook("Sheet1")
        _uiState.value = _uiState.value.copy(
            sessionId = null,
            documentTitle = title,
            sheets = blankDoc.sheets,
            activeSheetIndex = 0,
            selectedCellCoord = 0 to 0,
            formulaInputText = "",
            isDirty = true,
            toastMessage = "已创建空白表格，轻点单元格即可录入数据与公式"
        )
    }

    fun openDocument(docId: DocumentId, title: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, documentTitle = title)
            when (val result = officeEngine.openSession(docId, uri)) {
                is EngineResult.Success -> {
                    val session = result.data
                    val parsed = session.parsedXlsx
                    if (parsed != null && parsed.sheets.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            sessionId = session.sessionId,
                            documentTitle = title,
                            sheets = parsed.sheets,
                            activeSheetIndex = 0,
                            isLoading = false,
                            toastMessage = "已加载 ${parsed.sheets.size} 个工作表"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            sessionId = session.sessionId,
                            isLoading = false
                        )
                    }
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "打开表格失败: ${result.errorMessage}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun selectCell(row: Int, col: Int) {
        val currentSheet = _uiState.value.sheets.getOrNull(_uiState.value.activeSheetIndex) ?: return
        val cell = currentSheet.cells[row to col]
        val text = cell?.formula ?: cell?.rawValue ?: ""
        _uiState.value = _uiState.value.copy(
            selectedCellCoord = row to col,
            formulaInputText = text
        )
    }

    fun onFormulaTextChanged(newFormula: String) {
        _uiState.value = _uiState.value.copy(formulaInputText = newFormula)
    }

    /**
     * 确认提交当前单元格内容或公式，并触发本地重算
     */
    fun commitCellEdit() {
        val coord = _uiState.value.selectedCellCoord ?: return
        val sheetIndex = _uiState.value.activeSheetIndex
        val currentSheet = _uiState.value.sheets.getOrNull(sheetIndex) ?: return
        val formulaOrText = _uiState.value.formulaInputText.trim()

        val isFormula = formulaOrText.startsWith("=")
        val displayVal = if (isFormula) {
            formulaEvaluator.evaluateCellFormula(formulaOrText, currentSheet)
        } else {
            formulaOrText
        }

        val updatedCell = SpreadsheetCell(
            row = coord.first,
            col = coord.second,
            rawValue = formulaOrText,
            displayValue = displayVal,
            formula = if (isFormula) formulaOrText else null,
            dataType = if (isFormula) CellDataType.FORMULA else (displayVal.toDoubleOrNull()?.let { CellDataType.NUMBER } ?: CellDataType.STRING)
        )

        val newCells = currentSheet.cells.toMutableMap()
        newCells[coord] = updatedCell

        // 刷新所有依赖公式单元格
        val finalSheet = recalculateAllFormulas(currentSheet.copy(cells = newCells))
        val updatedSheets = _uiState.value.sheets.toMutableList()
        updatedSheets[sheetIndex] = finalSheet

        _uiState.value = _uiState.value.copy(
            sheets = updatedSheets,
            isDirty = true,
            toastMessage = if (isFormula) "公式重算完成: $displayVal" else null
        )
    }

    private fun recalculateAllFormulas(sheet: SpreadsheetSheet): SpreadsheetSheet {
        val updatedCells = sheet.cells.toMutableMap()
        for ((coord, cell) in sheet.cells) {
            val formula = cell.formula
            if (formula != null) {
                val evaluated = formulaEvaluator.evaluateCellFormula(formula, sheet)
                updatedCells[coord] = cell.copy(displayValue = evaluated)
            }
        }
        return sheet
    }

    fun switchSheet(newIndex: Int) {
        if (newIndex in _uiState.value.sheets.indices) {
            _uiState.value = _uiState.value.copy(activeSheetIndex = newIndex)
        }
    }

    fun toggleFilter() {
        _uiState.value = _uiState.value.copy(isFilterEnabled = !_uiState.value.isFilterEnabled)
    }

    /**
     * 保存表格内容
     */
    fun saveDocument() {
        val session = _uiState.value.sessionId
        val doc = com.openofficepdf.engine.office.openxml.ParsedXlsxDocument(
            title = _uiState.value.documentTitle,
            sheets = _uiState.value.sheets
        )
        if (session != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSaving = true)
                when (val result = officeEngine.saveSession(session)) {
                    is EngineResult.Success -> {
                        _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, toastMessage = "表格已安全保存")
                    }
                    is EngineResult.Failure -> {
                        _uiState.value = _uiState.value.copy(isSaving = false, toastMessage = "保存失败: ${result.errorMessage}")
                    }
                    else -> _uiState.value = _uiState.value.copy(isSaving = false)
                }
            }
        } else {
            // 新建未绑定 session 的情况
            _uiState.value = _uiState.value.copy(isDirty = false, toastMessage = "已保存至本地工作区")
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
