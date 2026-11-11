package com.openofficepdf.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class PrimaryWorkspaceEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val routeKey: String
)

val PrimaryEntries = listOf(
    PrimaryWorkspaceEntry("PDF 工作台", "无极缩放/批注/原位编辑/真脱敏", Icons.Default.PictureAsPdf, "pdf"),
    PrimaryWorkspaceEntry("Word 工作台", "DOCX编辑/移动端自适应重排/导出", Icons.Default.Description, "word"),
    PrimaryWorkspaceEntry("Excel 工作台", "XLSX编辑/常用公式重算/冻结窗格", Icons.Default.TableChart, "excel"),
    PrimaryWorkspaceEntry("PPT 演示文稿", "高保真幻灯片放映/手写墨迹批注", Icons.Default.Slideshow, "ppt"),
    PrimaryWorkspaceEntry("Markdown 工作台", "双栏同步/离线Mermaid/KaTeX公式", Icons.Default.EditNote, "markdown"),
    PrimaryWorkspaceEntry("OCR 识别中心", "端侧PP-Structure/表格还原/双层PDF", Icons.Default.DocumentScanner, "ocr"),
    PrimaryWorkspaceEntry("格式转换中心", "多格式互转/分级提示/断点续传", Icons.Default.Transform, "converter")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEntryClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenOfficePDF 离线办公") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "离线综合文档工作台",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(PrimaryEntries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clickable { onEntryClick(entry.routeKey) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = entry.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
