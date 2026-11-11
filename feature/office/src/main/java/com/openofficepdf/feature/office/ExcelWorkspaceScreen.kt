package com.openofficepdf.feature.office

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openofficepdf.engine.office.CellDataType
import com.openofficepdf.engine.office.SpreadsheetSheet

/**
 * Excel (XLSX) 交互工作台屏幕 (Jetpack Compose 原生构建)
 * - 顶部公式栏（支持 fx 输入、语法重算与断言）
 * - 冻结窗格与虚拟化大网格 (LazyColumn + Row)
 * - 底部多工作表 (Sheet Tabs) 切换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelWorkspaceScreen(
    viewModel: ExcelWorkspaceViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    val activeSheet = uiState.sheets.getOrNull(uiState.activeSheetIndex)
        ?: SpreadsheetSheet(0, "Sheet1")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.documentTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (uiState.isDirty) {
                            Text("已编辑 (未保存)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFilter() }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "自动筛选",
                            tint = if (uiState.isFilterEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { /* 导出 PDF */ }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "导出为 PDF")
                    }
                    IconButton(onClick = { /* 事务保存 */ }) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                }
            )
        },
        bottomBar = {
            // 底部工作表 (Sheet) 切换标签栏
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.activeSheetIndex,
                    edgePadding = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.sheets.forEachIndexed { index, sheet ->
                        Tab(
                            selected = uiState.activeSheetIndex == index,
                            onClick = { viewModel.switchSheet(index) },
                            text = { Text(sheet.sheetName) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F7F7))
        ) {
            // 顶部公式栏
            SpreadsheetFormulaBar(
                selectedCoord = uiState.selectedCellCoord,
                formulaText = uiState.formulaInputText,
                onFormulaChanged = { viewModel.onFormulaTextChanged(it) },
                onCommit = { viewModel.commitCellEdit() }
            )

            // 核心虚拟大网格
            SpreadsheetGrid(
                sheet = activeSheet,
                selectedCoord = uiState.selectedCellCoord,
                onCellSelected = { r, c -> viewModel.selectCell(r, c) }
            )
        }
    }
}

/**
 * 顶部 Excel 公式编辑栏
 */
@Composable
fun SpreadsheetFormulaBar(
    selectedCoord: Pair<Int, Int>?,
    formulaText: String,
    onFormulaChanged: (String) -> Unit,
    onCommit: () -> Unit
) {
    val coordName = if (selectedCoord != null) {
        val colLetter = ('A'.code + selectedCoord.second).toChar()
        "$colLetter${selectedCoord.first + 1}"
    } else {
        "A1"
    }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = coordName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "fx",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = formulaText,
                onValueChange = onFormulaChanged,
                placeholder = { Text("输入内容或公式 (如 =SUM(B2:B10))", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onCommit) {
                Icon(Icons.Default.Check, contentDescription = "提交公式", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 虚拟化大网格实现 (支持横向/纵向联合滚动与首行冻结呈现)
 */
@Composable
fun SpreadsheetGrid(
    sheet: SpreadsheetSheet,
    selectedCoord: Pair<Int, Int>?,
    onCellSelected: (Int, Int) -> Unit
) {
    val hScrollState = rememberScrollState()
    val cellWidth = 90.dp
    val headerHeight = 30.dp
    val rowHeight = 36.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScrollState)
    ) {
        Column {
            // 列头 (A, B, C, D...)
            Row(
                modifier = Modifier
                    .background(Color(0xFFEFEFEF))
                    .border(1.dp, Color(0xFFD4D4D4))
            ) {
                // 左上角空白锚点
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = headerHeight)
                        .border(0.5.dp, Color(0xFFD4D4D4))
                )
                for (c in 0 until sheet.totalCols) {
                    val colLetter = ('A'.code + c).toChar().toString()
                    Box(
                        modifier = Modifier
                            .size(width = cellWidth, height = headerHeight)
                            .border(0.5.dp, Color(0xFFD4D4D4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = colLetter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            // 数据行虚拟列表
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                items(sheet.totalRows) { r ->
                    Row(modifier = Modifier.height(rowHeight)) {
                        // 行号 (1, 2, 3...)
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = rowHeight)
                                .background(Color(0xFFEFEFEF))
                                .border(0.5.dp, Color(0xFFD4D4D4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (r + 1).toString(),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        // 每一列单元格
                        for (c in 0 until sheet.totalCols) {
                            val cell = sheet.cells[r to c]
                            val isSelected = (selectedCoord?.first == r && selectedCoord.second == c)

                            Box(
                                modifier = Modifier
                                    .size(width = cellWidth, height = rowHeight)
                                    .background(if (isSelected) Color(0xFFE3F2FD) else Color.White)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0)
                                    )
                                    .clickable { onCellSelected(r, c) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (cell != null) {
                                    Text(
                                        text = cell.displayValue,
                                        fontSize = 13.sp,
                                        fontWeight = if (cell.isBold) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = if (cell.dataType == CellDataType.NUMBER) TextAlign.End else TextAlign.Start,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
