package com.openofficepdf.feature.office

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Word (DOCX) 交互工作台主界面 (Jetpack Compose 原生构建)
 * - 顶部工具栏（视图切换、加粗、斜体、插入表格、保存）
 * - 移动端自适应重排视图 vs 原始打印视图无缝切换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordWorkspaceScreen(
    viewModel: WordWorkspaceViewModel,
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
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) { }
                            val title = uri.lastPathSegment?.substringAfterLast('/') ?: "文档.docx"
                            viewModel.openDocument(com.openofficepdf.core.model.DocumentId(uri.toString()), title, uri)
                        }
                    }

                    IconButton(onClick = { launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "*/*")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "打开文档")
                    }

                    // 自适应排版 vs 打印排版一键切换
                    FilledTonalButton(
                        onClick = { viewModel.toggleViewMode() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == WordLayoutViewMode.MOBILE_REFLOW) Icons.Default.PhoneAndroid else Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.viewMode == WordLayoutViewMode.MOBILE_REFLOW) "自适应视图" else "打印视图",
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        enabled = !uiState.isSaving,
                        onClick = { viewModel.saveDocument() }
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 富文本快捷排版工具栏
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleBold() }) {
                        Icon(
                            Icons.Default.FormatBold,
                            contentDescription = "加粗",
                            tint = if (uiState.isBold) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { viewModel.toggleItalic() }) {
                        Icon(
                            Icons.Default.FormatItalic,
                            contentDescription = "斜体",
                            tint = if (uiState.isItalic) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { viewModel.toggleUnderline() }) {
                        Icon(
                            Icons.Default.FormatUnderlined,
                            contentDescription = "下划线",
                            tint = if (uiState.isUnderline) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { viewModel.insertTable(3, 3) }) {
                        Icon(Icons.Default.TableChart, contentDescription = "插入表格")
                    }
                    IconButton(onClick = { /* 插入图片 */ }) {
                        Icon(Icons.Default.Image, contentDescription = "插入图片")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (uiState.viewMode == WordLayoutViewMode.PRINT_LAYOUT) Color(0xFFE8E8E8) else Color(0xFFFAFAFA))
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // Word 画板区域
                WordDocumentSurface(viewMode = uiState.viewMode, parsedDocx = uiState.parsedDocx)
            }
        }
    }
}

/**
 * 依据当前模式呈现的文档物理纸张或自适应流式画板
 */
@Composable
fun WordDocumentSurface(
    viewMode: WordLayoutViewMode,
    parsedDocx: com.openofficepdf.engine.office.openxml.ParsedDocxDocument?
) {
    val blocks = parsedDocx?.blocks

    if (viewMode == WordLayoutViewMode.PRINT_LAYOUT) {
        // 打印视图：仿照物理 A4 纸张呈现（带阴影与页边距）
        androidx.compose.foundation.lazy.LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Surface(
                    shadowElevation = 6.dp,
                    shape = RoundedCornerShape(2.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .border(1.dp, Color(0xFFD0D0D0))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "页眉：${parsedDocx?.title ?: "OpenOfficePDF 离线文档"}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (blocks.isNullOrEmpty()) {
                            Text(
                                text = "DOCX 打印版式排版预览",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "当前处于固定 A4 纸张打印模式。点击右上角文件夹图标可打开手机本地真实 .docx 文件。",
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        } else {
                            blocks.forEach { block ->
                                when (block) {
                                    is com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock -> {
                                        val p = block.paragraph
                                        Text(
                                            text = p.fullText,
                                            fontSize = if (p.isHeading) (14 + maxOf(0, 6 - p.headingLevel.coerceIn(1, 6)) * 2).sp else 14.sp,
                                            fontWeight = if (p.isHeading || p.runs.any { it.isBold }) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (p.runs.any { it.isItalic }) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    is com.openofficepdf.engine.office.openxml.DocxBlock.TableBlock -> {
                                        // 简单表格展示
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color.LightGray)
                                                .padding(4.dp)
                                        ) {
                                            block.table.rows.forEach { row ->
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    row.forEach { cell ->
                                                        Text(
                                                            text = cell.text,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .border(0.5.dp, Color.LightGray)
                                                                .padding(4.dp)
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
                }
            }
        }
    } else {
        // 移动端自适应重排视图：去除固定纸张边界与横向滚动限制，边距收紧自适应屏幕
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("移动端自适应排版已激活：${parsedDocx?.totalWords ?: 0} 字，自动换行无横向滚动", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            if (blocks.isNullOrEmpty()) {
                item {
                    Text(
                        text = "DOCX 移动端流式重排排版",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "本模式专为手机屏幕打造，通过 OpenXML 引擎重排 DOM 节点，使段落和表格自适应手机屏幕宽度，无需频繁双指缩放和横向平移，阅读与打字输入体验倍增。",
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                }
            } else {
                items(blocks.size) { idx ->
                    when (val block = blocks[idx]) {
                        is com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock -> {
                            val p = block.paragraph
                            Text(
                                text = p.fullText,
                                fontSize = if (p.isHeading) (14 + maxOf(0, 6 - p.headingLevel.coerceIn(1, 6)) * 2).sp else 15.sp,
                                fontWeight = if (p.isHeading || p.runs.any { it.isBold }) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (p.runs.any { it.isItalic }) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        is com.openofficepdf.engine.office.openxml.DocxBlock.TableBlock -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(4.dp)
                            ) {
                                block.table.rows.forEach { row ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell.text,
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    .padding(6.dp)
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
    }
}
