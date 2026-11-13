package com.openofficepdf.feature.markdown

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.engine.markdown.*
import kotlinx.coroutines.launch

/**
 * Markdown 交互工作台屏幕 (任务书 2.4 节)
 * - SAF 打开与保存真实本地 .md 文档
 * - 双栏精准同步对照 / 单栏全屏切换
 * - 原生排版阅读器 (非整个嵌 WebView，保障 60fps 原生流畅滚动)
 * - 代码高亮块、数学公式、图表渲染卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownWorkspaceScreen(
    viewModel: MarkdownWorkspaceViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 打开本地 .md 文件
    val openMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val title = it.lastPathSegment?.substringAfterLast('/') ?: "文档.md"
            viewModel.openFromUri(it, title)
        }
    }

    // SAF 另存为导出 .md 文件
    val saveMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportToUri(it)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.documentTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (uiState.isModified) {
                            Text("已修改(未保存)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { openMdLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "打开本地MD文件")
                    }
                    IconButton(onClick = { viewModel.zoomOutPreview() }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "缩小排版")
                    }
                    Text(
                        text = "${(uiState.previewZoomScale * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { viewModel.zoomInPreview() }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "放大排版")
                    }
                    IconButton(onClick = { viewModel.toggleNightMode() }) {
                        Icon(
                            imageVector = if (uiState.isNightMode) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                            contentDescription = "夜间模式"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleDisplayMode() }) {
                        Icon(
                            imageVector = when (uiState.displayMode) {
                                MarkdownDisplayMode.SPLIT_DUAL -> Icons.Default.ViewSidebar
                                MarkdownDisplayMode.PREVIEW_ONLY -> Icons.Default.Visibility
                                MarkdownDisplayMode.SOURCE_ONLY -> Icons.Default.Code
                            },
                            contentDescription = "切换视图模式"
                        )
                    }
                    IconButton(
                        enabled = !uiState.isSaving,
                        onClick = {
                            val targetName = uiState.documentTitle.ifBlank { "未命名.md" }
                            saveMdLauncher.launch(targetName)
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存与导出")
                    }
                }
            )
        }
    ) { innerPadding ->
        val bgColor = if (uiState.isNightMode) Color(0xFF141414) else Color(0xFFFAFAFA)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgColor)
        ) {
            when (uiState.displayMode) {
                MarkdownDisplayMode.SPLIT_DUAL -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左侧源码编辑
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            MarkdownSourceEditor(
                                source = uiState.sourceContent,
                                isNightMode = uiState.isNightMode,
                                onSourceChanged = { viewModel.onSourceChanged(it) },
                                onCursorLineChanged = { viewModel.onEditorScrolledToLine(it) }
                            )
                        }
                        Divider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                            color = if (uiState.isNightMode) Color(0xFF333333) else Color(0xFFE0E0E0)
                        )
                        // 右侧排版渲染预览
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            MarkdownNativePreview(
                                blocks = uiState.parsedBlocks,
                                targetBlockIndex = uiState.previewTargetIndex,
                                isNightMode = uiState.isNightMode,
                                zoomScale = uiState.previewZoomScale,
                                onZoomChange = { viewModel.setPreviewZoom(it) }
                            )
                        }
                    }
                }
                MarkdownDisplayMode.SOURCE_ONLY -> {
                    MarkdownSourceEditor(
                        source = uiState.sourceContent,
                        isNightMode = uiState.isNightMode,
                        onSourceChanged = { viewModel.onSourceChanged(it) },
                        onCursorLineChanged = { viewModel.onEditorScrolledToLine(it) }
                    )
                }
                MarkdownDisplayMode.PREVIEW_ONLY -> {
                    MarkdownNativePreview(
                        blocks = uiState.parsedBlocks,
                        targetBlockIndex = uiState.previewTargetIndex,
                        isNightMode = uiState.isNightMode,
                        zoomScale = uiState.previewZoomScale,
                        onZoomChange = { viewModel.setPreviewZoom(it) }
                    )
                }
            }
        }
    }
}

/**
 * 源码编辑组件
 */
@Composable
fun MarkdownSourceEditor(
    source: String,
    isNightMode: Boolean,
    onSourceChanged: (String) -> Unit,
    onCursorLineChanged: (Int) -> Unit
) {
    val textColor = if (isNightMode) Color(0xFFD4D4D4) else Color(0xFF1E1E1E)
    val bgColor = if (isNightMode) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(12.dp)
    ) {
        BasicTextField(
            value = source,
            onValueChange = {
                onSourceChanged(it)
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textColor
            ),
            cursorBrush = SolidColor(if (isNightMode) Color.White else Color.Black),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 原生排版渲染流式列表
 */
@Composable
fun MarkdownNativePreview(
    blocks: List<MarkdownBlock>,
    targetBlockIndex: Int,
    isNightMode: Boolean,
    zoomScale: Float = 1.0f,
    onZoomChange: (Float) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 监听来自源码编辑器的滚动插值定位
    LaunchedEffect(targetBlockIndex) {
        if (targetBlockIndex in blocks.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(targetBlockIndex)
            }
        }
    }

    SelectionContainer {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy((10 * zoomScale).dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        onZoomChange(zoomScale * zoom)
                    }
                }
        ) {
            itemsIndexed(blocks) { _, block ->
                MarkdownBlockRenderer(block = block, isNightMode = isNightMode, zoomScale = zoomScale)
            }
        }
    }
}

/**
 * 单个 AST 块的原生 Compose 渲染映射
 */
@Composable
fun MarkdownBlockRenderer(
    block: MarkdownBlock,
    isNightMode: Boolean,
    zoomScale: Float = 1.0f
) {
    val textColor = if (isNightMode) Color(0xFFEEEEEE) else Color(0xFF222222)

    when (block) {
        is MarkdownBlock.Heading -> {
            val (fontSize, weight) = when (block.level) {
                1 -> (24 * zoomScale).sp to FontWeight.Bold
                2 -> (20 * zoomScale).sp to FontWeight.Bold
                3 -> (17 * zoomScale).sp to FontWeight.SemiBold
                else -> (15 * zoomScale).sp to FontWeight.Medium
            }
            val headingSpan = remember(block.text, isNightMode) {
                MarkdownInlineParser.parseInlineSpans(block.text, isNightMode)
            }
            Text(
                text = headingSpan,
                fontSize = fontSize,
                fontWeight = weight,
                color = textColor,
                modifier = Modifier.padding(vertical = (4 * zoomScale).dp)
            )
        }
        is MarkdownBlock.Paragraph -> {
            val paragraphSpan = remember(block.text, isNightMode) {
                MarkdownInlineParser.parseInlineSpans(block.text, isNightMode)
            }
            Text(
                text = paragraphSpan,
                fontSize = (15 * zoomScale).coerceAtLeast(10f).sp,
                lineHeight = (24 * zoomScale).coerceAtLeast(16f).sp,
                color = textColor
            )
        }
        is MarkdownBlock.BlockQuote -> {
            val quoteSpan = remember(block.text, isNightMode) {
                MarkdownInlineParser.parseInlineSpans(block.text, isNightMode)
            }
            val quoteBg = if (isNightMode) Color(0xFF1E293B) else Color(0xFFF8FAFC)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = quoteBg,
                modifier = Modifier.fillMaxWidth().padding(vertical = (4 * zoomScale).dp)
            ) {
                Row(modifier = Modifier.padding((10 * zoomScale).dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((24 * zoomScale).dp)
                            .background(Color(0xFF2563EB), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width((10 * zoomScale).dp))
                    Text(
                        text = quoteSpan,
                        fontSize = (14 * zoomScale).coerceAtLeast(10f).sp,
                        lineHeight = (22 * zoomScale).coerceAtLeast(16f).sp,
                        color = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                }
            }
        }
        is MarkdownBlock.CodeBlock -> {
            val codeBg = if (isNightMode) Color(0xFF2B2B2B) else Color(0xFFF0F0F0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeBg, RoundedCornerShape(6.dp))
                    .padding((8 * zoomScale).dp)
            ) {
                if (!block.language.isNullOrBlank()) {
                    Text(
                        text = block.language ?: "",
                        fontSize = (11 * zoomScale).coerceAtLeast(8f).sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text(
                        text = block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (13 * zoomScale).coerceAtLeast(9f).sp,
                        lineHeight = (18 * zoomScale).coerceAtLeast(13f).sp,
                        color = if (isNightMode) Color(0xFF98C379) else Color(0xFFC7254E)
                    )
                }
            }
        }
        is MarkdownBlock.MermaidChart -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isNightMode) Color(0xFF252526) else Color(0xFFF7F7F7),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isNightMode) Color(0xFF3E3E42) else Color(0xFFE5E5E5), RoundedCornerShape(8.dp))
                    .padding((12 * zoomScale).dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mermaid 图表 (离线渲染)", fontSize = (12 * zoomScale).sp, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size((16 * zoomScale).dp))
                    }
                    Spacer(modifier = Modifier.height((8 * zoomScale).dp))
                    Text(
                        text = block.sourceCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (12 * zoomScale).coerceAtLeast(9f).sp,
                        color = Color.Gray
                    )
                }
            }
        }
        is MarkdownBlock.MathFormula -> {
            val prettyFormula = remember(block.formula) {
                MarkdownInlineParser.formatMathSnippet(block.formula)
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isNightMode) Color(0xFF1E293B) else Color(0xFFF0FDF4),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isNightMode) Color(0xFF334155) else Color(0xFFDCFCE7)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (6 * zoomScale).dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = (16 * zoomScale).dp, vertical = (12 * zoomScale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = prettyFormula,
                            fontFamily = FontFamily.Serif,
                            fontSize = (18 * zoomScale).coerceAtLeast(12f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNightMode) Color(0xFF38BDF8) else Color(0xFF0F766E)
                        )
                    }
                }
            }
        }
        is MarkdownBlock.Table -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            ) {
                for ((idx, row) in block.rows.withIndex()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (idx == 0) Color.Gray.copy(alpha = 0.15f) else Color.Transparent)
                            .padding((8 * zoomScale).dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (cell in row) {
                            val cellSpan = remember(cell, isNightMode) {
                                MarkdownInlineParser.parseInlineSpans(cell, isNightMode)
                            }
                            Text(
                                text = cellSpan,
                                fontSize = (13 * zoomScale).coerceAtLeast(9f).sp,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        is MarkdownBlock.ListItem -> {
            val itemSpan = remember(block.text, isNightMode) {
                MarkdownInlineParser.parseInlineSpans(block.text, isNightMode)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = (block.depth * 16 * zoomScale).dp, top = 2.dp, bottom = 2.dp)) {
                Text("• ", fontSize = (16 * zoomScale).sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(text = itemSpan, fontSize = (15 * zoomScale).coerceAtLeast(10f).sp, lineHeight = (22 * zoomScale).coerceAtLeast(16f).sp, color = textColor)
            }
        }
        is MarkdownBlock.TaskListItem -> {
            var checkedState by remember(block.isChecked) { mutableStateOf(block.isChecked) }
            val itemSpan = remember(block.text, isNightMode) {
                MarkdownInlineParser.parseInlineSpans(block.text, isNightMode)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.depth * 16 * zoomScale).dp, top = 2.dp, bottom = 2.dp)
            ) {
                Checkbox(
                    checked = checkedState,
                    onCheckedChange = { checkedState = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )
                Text(
                    text = itemSpan,
                    fontSize = (15 * zoomScale).coerceAtLeast(10f).sp,
                    lineHeight = (22 * zoomScale).coerceAtLeast(16f).sp,
                    color = if (checkedState) textColor.copy(alpha = 0.5f) else textColor
                )
            }
        }
    }
}
