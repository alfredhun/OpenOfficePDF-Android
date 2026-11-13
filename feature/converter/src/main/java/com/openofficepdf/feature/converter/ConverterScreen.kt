package com.openofficepdf.feature.converter

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.openofficepdf.core.model.DocumentFormat
import com.openofficepdf.engine.converter.ConversionTier

/**
 * 格式转换中心交互界面 (任务书 2.6 节)
 * - 源格式与目标格式选择 (集成 SAF 文件选择器与目标文件保存)
 * - 转换质量分级与诚实预警卡片 (绝不许诺虚假无损)
 * - 异步任务流式进度与逐页断点恢复
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 选择待转换的源文件
    val openSourceFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "文档"
            val format = when {
                name.endsWith(".docx", ignoreCase = true) -> DocumentFormat.DOCX
                name.endsWith(".xlsx", ignoreCase = true) -> DocumentFormat.XLSX
                name.endsWith(".pptx", ignoreCase = true) -> DocumentFormat.PPTX
                name.endsWith(".pdf", ignoreCase = true) -> DocumentFormat.PDF
                else -> DocumentFormat.PDF
            }
            viewModel.selectSourceDocument(it, name, format)
        }
    }

    // SAF 保存转换后的目标文件
    val saveTargetFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { outputUri: Uri? ->
        outputUri?.let {
            viewModel.startConversionToUri(it)
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
                title = { Text("多格式离线转换中心", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        openSourceFileLauncher.launch(arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        ))
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择本地源文件")
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
            // 源文件与目标格式选择器
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("转换方向选择", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (uiState.sourceFileName.isNotBlank()) "源文件: ${uiState.sourceFileName} (${uiState.sourceFormat.name})" else "未选择文件 (点击右上角打开)",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Gray)

                        // 目标格式选择下拉或切换
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(DocumentFormat.PDF, DocumentFormat.DOCX, DocumentFormat.XLSX).forEach { fmt ->
                                if (fmt != uiState.sourceFormat) {
                                    FilterChip(
                                        selected = uiState.targetFormat == fmt,
                                        onClick = { viewModel.setTargetFormat(fmt) },
                                        label = { Text(fmt.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 任务书强制要求的诚实预警卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (uiState.qualityAssessment.tier) {
                        ConversionTier.LOSSLESS -> MaterialTheme.colorScheme.primaryContainer
                        ConversionTier.LAYOUT_REBUILD -> MaterialTheme.colorScheme.tertiaryContainer
                        ConversionTier.OCR_REBUILD -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (uiState.qualityAssessment.tier) {
                                ConversionTier.LOSSLESS -> Icons.Default.CheckCircle
                                ConversionTier.LAYOUT_REBUILD -> Icons.Default.Info
                                ConversionTier.OCR_REBUILD -> Icons.Default.Warning
                            },
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "预期质量分级: ${uiState.qualityAssessment.tier.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.qualityAssessment.titleWarning,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.qualityAssessment.detailedNotice,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 转换进度与状态指示卡片
            if (uiState.isConverting || uiState.currentProgress > 0f) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (uiState.isConverting) "正在执行本地转换..." else "转换已完成",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.currentProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.currentStageText,
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部操作区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isConverting) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.cancelConversion() }
                    ) {
                        Text("暂停 / 取消转换")
                    }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.selectedSourceUri != null,
                        onClick = {
                            val ext = uiState.targetFormat.name.lowercase()
                            val baseName = uiState.sourceFileName.substringBeforeLast('.')
                            saveTargetFileLauncher.launch("${baseName}_converted.$ext")
                        }
                    ) {
                        Icon(Icons.Default.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择目标位置并开始转换")
                    }
                }
            }
        }
    }
}
