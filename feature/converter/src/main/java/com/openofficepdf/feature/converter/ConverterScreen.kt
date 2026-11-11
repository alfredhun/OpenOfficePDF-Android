package com.openofficepdf.feature.converter

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
 * - 源格式与目标格式选择
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
                                text = "源格式: ${uiState.sourceFormat.name}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Gray)

                        // 目标格式选择切换
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(DocumentFormat.DOCX, DocumentFormat.XLSX, DocumentFormat.PDF).forEach { fmt ->
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

            // 转换质量分级与诚实预警卡片 (任务书强制规范)
            val assessment = uiState.qualityAssessment
            val tierContainerColor = when (assessment.tier) {
                ConversionTier.LOSSLESS -> MaterialTheme.colorScheme.primaryContainer
                ConversionTier.LAYOUT_REBUILD -> MaterialTheme.colorScheme.tertiaryContainer
                ConversionTier.OCR_REBUILD -> MaterialTheme.colorScheme.errorContainer
            }
            val tierContentColor = when (assessment.tier) {
                ConversionTier.LOSSLESS -> MaterialTheme.colorScheme.onPrimaryContainer
                ConversionTier.LAYOUT_REBUILD -> MaterialTheme.colorScheme.onTertiaryContainer
                ConversionTier.OCR_REBUILD -> MaterialTheme.colorScheme.onErrorContainer
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = tierContainerColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (assessment.tier == ConversionTier.LOSSLESS) Icons.Default.Verified else Icons.Default.Info,
                                contentDescription = null,
                                tint = tierContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = assessment.titleWarning,
                                fontWeight = FontWeight.Bold,
                                color = tierContentColor,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = "保真度 ~${assessment.fidelityPercentageEstimate}%",
                            fontWeight = FontWeight.Bold,
                            color = tierContentColor,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = assessment.detailedNotice,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = tierContentColor
                    )
                }
            }

            // 任务进度与流式执行
            if (uiState.isConverting || uiState.completedPages > 0) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (uiState.isConverting) "正在转换处理..." else "转换已就绪",
                            fontWeight = FontWeight.SemiBold,
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
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部操作按钮
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
                        onClick = { viewModel.startConversion() }
                    ) {
                        Icon(Icons.Default.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("开始离线转换 (${uiState.sourceFormat.name} -> ${uiState.targetFormat.name})")
                    }
                }
            }
        }
    }
}
