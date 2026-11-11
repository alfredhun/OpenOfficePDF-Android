package com.openofficepdf.feature.ocr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线 OCR 识别中心", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                }
            }

            // 识别结果列表与置信度高亮
            Text("识别结果预览", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            // 底部操作区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = uiState.completedPages > 0,
                    onClick = { /* 导出双层可搜索 PDF */ }
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("合成双层 PDF")
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = uiState.completedPages > 0,
                    onClick = { /* 导出 Word / Excel */ }
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导出 Office")
                }
            }
        }
    }
}
