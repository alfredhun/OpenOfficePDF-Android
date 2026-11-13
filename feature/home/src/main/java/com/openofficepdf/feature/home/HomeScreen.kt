package com.openofficepdf.feature.home

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openofficepdf.core.database.entity.DocumentEntity
import java.text.SimpleDateFormat
import java.util.*

data class QuickFeatureItem(
    val name: String,
    val icon: ImageVector,
    val routeKey: String,
    val iconColor: Color,
    val bgColor: Color,
    val badge: String? = null
)

val QuickFeatures = listOf(
    QuickFeatureItem(
        name = "PDF 阅读",
        icon = Icons.Default.PictureAsPdf,
        routeKey = "pdf",
        iconColor = Color(0xFFE11D48),
        bgColor = Color(0xFFFFE4E6)
    ),
    QuickFeatureItem(
        name = "Word 重排",
        icon = Icons.Default.Description,
        routeKey = "word",
        iconColor = Color(0xFF2563EB),
        bgColor = Color(0xFFDBEAFE)
    ),
    QuickFeatureItem(
        name = "Excel 表格",
        icon = Icons.Default.TableChart,
        routeKey = "excel",
        iconColor = Color(0xFF059669),
        bgColor = Color(0xFFD1FAE5)
    ),
    QuickFeatureItem(
        name = "PPT 演示",
        icon = Icons.Default.Slideshow,
        routeKey = "ppt",
        iconColor = Color(0xFFD97706),
        bgColor = Color(0xFFFEF3C7)
    ),
    QuickFeatureItem(
        name = "Markdown",
        icon = Icons.Default.EditNote,
        routeKey = "markdown",
        iconColor = Color(0xFF7C3AED),
        bgColor = Color(0xFFEDE9FE)
    ),
    QuickFeatureItem(
        name = "离线 OCR",
        icon = Icons.Default.DocumentScanner,
        routeKey = "ocr",
        iconColor = Color(0xFF0891B2),
        bgColor = Color(0xFFCFFAFE),
        badge = "AI"
    ),
    QuickFeatureItem(
        name = "格式转换",
        icon = Icons.Default.Transform,
        routeKey = "converter",
        iconColor = Color(0xFF4F46E5),
        bgColor = Color(0xFFE0E7FF)
    ),
    QuickFeatureItem(
        name = "系统设置",
        icon = Icons.Default.Tune,
        routeKey = "settings",
        iconColor = Color(0xFF475569),
        bgColor = Color(0xFFF1F5F9)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentDocuments: List<DocumentEntity> = emptyList(),
    onEntryClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit = {},
    onCreateNewDoc: (String) -> Unit = {}
) {
    var showOfflineDialog by remember { mutableStateOf(false) }

    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("100% 离线纯净架构", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• 绝不申请 INTERNET 权限，底座无网络套接字。", fontSize = 13.sp)
                    Text("• 文档解析、OCR、渲染、转换 100% 本地运算。", fontSize = 13.sp)
                    Text("• 适配 Android 15 16KB 内存页面对齐，稳定高效。", fontSize = 13.sp)
                    Text("• 数据存储于私有沙盒与 SAF，绝不上报隐私。", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showOfflineDialog = false }) {
                    Text("了解并返回")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OpenOfficePDF",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "旗舰级现代离线办公套件",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 精致离线胶囊微章
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFD1FAE5),
                            modifier = Modifier
                                .clickable { showOfflineDialog = true }
                                .padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "100% 本地离线",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF065F46)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 2×4 紧凑功能网格
            item {
                Text(
                    text = "工作台应用",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val row1 = QuickFeatures.take(4)
                        val row2 = QuickFeatures.drop(4).take(4)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            row1.forEach { item ->
                                QuickFeatureGridCell(item = item, onClick = { onEntryClick(item.routeKey) })
                            }
                        }

                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 0.8.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            row2.forEach { item ->
                                QuickFeatureGridCell(item = item, onClick = { onEntryClick(item.routeKey) })
                            }
                        }
                    }
                }
            }

            // 2. 高颜值 "+ 空白新建" 快速横幅
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick("word") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "新建空白文档 / 笔记本",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "轻点开启本地 Word / Markdown 创作",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. 最近文档列表区域
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近文档 & 草稿",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${recentDocuments.size} 个文档",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (recentDocuments.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "暂无最近打开的文档",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "从上方工作台导入文件，或点击新建开启创作",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentDocuments) { doc ->
                    RecentDocumentCard(
                        doc = doc,
                        onClick = { onOpenDocument(doc) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFeatureGridCell(
    item: QuickFeatureItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(item.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.name,
                    tint = item.iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            item.badge?.let { badgeText ->
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RecentDocumentCard(
    doc: DocumentEntity,
    onClick: () -> Unit
) {
    val (iconColor, bgColor, formatLabel) = when (doc.format.lowercase()) {
        "pdf" -> Triple(Color(0xFFE11D48), Color(0xFFFFE4E6), "PDF")
        "docx" -> Triple(Color(0xFF2563EB), Color(0xFFDBEAFE), "DOCX")
        "xlsx" -> Triple(Color(0xFF059669), Color(0xFFD1FAE5), "XLSX")
        "pptx" -> Triple(Color(0xFFD97706), Color(0xFFFEF3C7), "PPTX")
        "markdown", "md" -> Triple(Color(0xFF7C3AED), Color(0xFFEDE9FE), "MD")
        else -> Triple(Color(0xFF64748B), Color(0xFFF1F5F9), doc.format.uppercase())
    }

    val dateStr = remember(doc.lastOpenedAt) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(doc.lastOpenedAt ?: doc.modifiedAt))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = iconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$dateStr · ${(doc.sizeBytes / 1024).coerceAtLeast(1)} KB",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
