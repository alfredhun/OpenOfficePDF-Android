package com.openofficepdf.feature.ocr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    viewModel: OcrViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var enableTableRecovery by remember { mutableStateOf(false) }

    // SAF 选择输入图片或 PDF
    val openSourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "文档"
            viewModel.selectDocument(it, name)
        }
    }

    // SAF 保存双层 PDF
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri: Uri? ->
        outputUri?.let {
            viewModel.synthesizeSearchablePdf(it)
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
                        Text("离线 OCR 识别中心", fontSize = 17.sp)
                        Text(uiState.documentTitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { openSourceLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择待识别文件")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 识别进度与断点状态卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.isRunning) "正在离线识别中..." else "准备就绪 / 已完成",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (uiState.lowConfidenceCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "${uiState.lowConfidenceCount} 处待校对",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uiState.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "已完成: ${uiState.completedPages} / ${uiState.totalPages} 页 (支持逐页断点断电恢复)",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = enableTableRecovery,
                                onCheckedChange = { enableTableRecovery = it }
                            )
                            Text("启用版面与表格分析", fontSize = 13.sp)
                        }

                        if (uiState.isRunning) {
                            OutlinedButton(onClick = { viewModel.pauseOrCancel() }) {
                                Text("暂停")
                            }
                        } else {
                            Button(
                                enabled = uiState.sourceUri != null,
                                onClick = { viewModel.startRecognition(enableTableRecovery) }
                            ) {
                                Text("开始识别")
                            }
                        }
                    }
                }
            }

            // 识别结果列表与置信度高亮
            Text("识别结果预览", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.pageResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.sourceUri == null) "请点击右上角打开图片或 PDF 文档" else "点击上方“开始识别”提取图文",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                items(uiState.pageResults) { page ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("第 ${page.pageIndex + 1} 页", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            for (block in page.textBlocks) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (charBox in block.characters) {
                                        val isLowConfidence = charBox.confidence < 0.80f
                                        Text(
                                            text = charBox.char,
                                            color = if (isLowConfidence) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                            fontWeight = if (isLowConfidence) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier
                                                .background(if (isLowConfidence) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部操作区：导出与双层 PDF 合成
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = uiState.completedPages > 0 && !uiState.isSynthesizing,
                    onClick = { createPdfLauncher.launch("Searchable_${System.currentTimeMillis()}.pdf") }
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.isSynthesizing) "正在合成..." else "合成双层 PDF")
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = uiState.completedPages > 0,
                    onClick = { /* 导出 Office */ }
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导出 Office")
                }
            }
        }
    }
}
