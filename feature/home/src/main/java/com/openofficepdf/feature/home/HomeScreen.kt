package com.openofficepdf.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PrimaryWorkspaceEntry(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val routeKey: String,
    val themeColor: Color,
    val tag: String? = null
)

val PrimaryEntries = listOf(
    PrimaryWorkspaceEntry(
        title = "PDF 专业工作台",
        subtitle = "PDF Studio",
        description = "无极缩放 · 批注高亮 · 原位编辑 · 物理脱敏",
        icon = Icons.Default.PictureAsPdf,
        routeKey = "pdf",
        themeColor = Color(0xFFE53935), // 经典专业朱红
        tag = "Core"
    ),
    PrimaryWorkspaceEntry(
        title = "Word 文档重排",
        subtitle = "Docx Flow",
        description = "移动端自适应重排 · 表格渲染 · 离线排版",
        icon = Icons.Default.Description,
        routeKey = "word",
        themeColor = Color(0xFF1E88E5), // 专业深海蓝
        tag = "OpenXML"
    ),
    PrimaryWorkspaceEntry(
        title = "Excel 表格工作台",
        subtitle = "Spreadsheet",
        description = "网格冻结 · 公式重算 · 离线分析 · 导出",
        icon = Icons.Default.TableChart,
        routeKey = "excel",
        themeColor = Color(0xFF2E7D32), // 经典林深绿
        tag = "OpenXML"
    ),
    PrimaryWorkspaceEntry(
        title = "PPT 演示演示",
        subtitle = "Presentation",
        description = "高保真幻灯放映 · 演讲手势 · 墨迹批注",
        icon = Icons.Default.Slideshow,
        routeKey = "ppt",
        themeColor = Color(0xFFD84315), // 温暖暖橙
        tag = "Viewer"
    ),
    PrimaryWorkspaceEntry(
        title = "Markdown 笔记",
        subtitle = "Markdown Lab",
        description = "双栏同步 · 离线Mermaid · KaTeX数学公式",
        icon = Icons.Default.EditNote,
        routeKey = "markdown",
        themeColor = Color(0xFF5E35B1), // 极客紫
        tag = "Native"
    ),
    PrimaryWorkspaceEntry(
        title = "离线 OCR 识别",
        subtitle = "AI Scanner",
        description = "版面直方图分割 · 表格还原 · 双层PDF合成",
        icon = Icons.Default.DocumentScanner,
        routeKey = "ocr",
        themeColor = Color(0xFF00897B), // 科技青绿
        tag = "Offline AI"
    ),
    PrimaryWorkspaceEntry(
        title = "格式转换中心",
        subtitle = "Batch Convert",
        description = "多格式互转 · 诚实质量分级 · 断点断电续转",
        icon = Icons.Default.Transform,
        routeKey = "converter",
        themeColor = Color(0xFF3949AB), // 靛蓝
        tag = "Pipeline"
    )
)

/**
 * 符合现代移动设计规范的精致主页
 * 1. 采用大字号标题与层次化 Hero Card
 * 2. 区分功能色彩主题（Red/Blue/Green/Purple/Teal/Indigo）
 * 3. 增强视觉质感：圆角卡片、低饱和度背景、高辨识度彩色微图标圆底、专属标签
 * 4. 100% 离线隐私盾徽章，建立极高信任感
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEntryClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OpenOfficePDF",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "100% 本地运行 · 纯净零网络传输",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部专属安全特征横幅 (Hero Card)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "端侧物理隔离保护",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "无 INTERNET",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "所有文件处理与 OCR 计算均在本地芯片执行，零云端回传，严守机密。",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // 模块小标题
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "工作台应用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            // 核心功能卡片网格
            items(PrimaryEntries) { entry ->
                ModernWorkspaceCard(
                    entry = entry,
                    onClick = { onEntryClick(entry.routeKey) }
                )
            }
        }
    }
}

@Composable
private fun ModernWorkspaceCard(
    entry: PrimaryWorkspaceEntry,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.5.dp,
            pressedElevation = 4.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 图标圆角容器（带微渐变/淡色背景）
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(entry.themeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.title,
                        tint = entry.themeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 类别徽标
                entry.tag?.let { tagText ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = tagText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.2).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
