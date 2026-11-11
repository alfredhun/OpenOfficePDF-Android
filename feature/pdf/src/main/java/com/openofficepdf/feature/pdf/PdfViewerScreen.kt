package com.openofficepdf.feature.pdf

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openofficepdf.core.model.pdf.PdfTextObject
import com.openofficepdf.engine.pdf.NormalizedCoordinatesTransformer

/**
 * PDF 交互阅读器与三级编辑画板 (Jetpack Compose 原生构建)
 * - 一级策略：覆盖式编辑 (带未清除红线预警)
 * - 二级策略：内容流对象级原位编辑 (点选文字对象 -> 原位修改 -> 自动字体回退)
 * - 真 Redaction 脱敏：物理移除相交文本流与涂黑位图
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOverlayDraftDialog by remember { mutableStateOf(false) }

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
                title = { Text(uiState.documentTitle, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleNightMode() }) {
                        Icon(
                            imageVector = if (uiState.isNightMode) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                            contentDescription = "夜间模式"
                        )
                    }

                    // 二级原位编辑
                    IconButton(onClick = {
                        val next = if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) PdfInteractionMode.VIEW else PdfInteractionMode.IN_PLACE_EDIT
                        viewModel.switchMode(next)
                    }) {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = "二级原位编辑",
                            tint = if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }

                    // 一级覆盖编辑
                    IconButton(onClick = {
                        val next = if (uiState.interactionMode == PdfInteractionMode.OVERLAY_EDIT) PdfInteractionMode.VIEW else PdfInteractionMode.OVERLAY_EDIT
                        viewModel.switchMode(next)
                    }) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = "一级覆盖编辑",
                            tint = if (uiState.interactionMode == PdfInteractionMode.OVERLAY_EDIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }

                    // 真 Redaction 脱敏
                    IconButton(onClick = {
                        val next = if (uiState.interactionMode == PdfInteractionMode.TRUE_REDACTION) PdfInteractionMode.VIEW else PdfInteractionMode.TRUE_REDACTION
                        viewModel.switchMode(next)
                    }) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "真 Redaction 脱敏",
                            tint = if (uiState.interactionMode == PdfInteractionMode.TRUE_REDACTION) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }
                }
            )
        },
        bottomBar = {
            when (uiState.interactionMode) {
                PdfInteractionMode.OVERLAY_EDIT -> {
                    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("拖拽框选需要覆盖的矩形区域", style = MaterialTheme.typography.bodySmall)
                            Button(
                                enabled = uiState.activeOverlayDraft?.targetRegionPt?.isEmpty == false,
                                onClick = { showOverlayDraftDialog = true }
                            ) {
                                Text("输入覆盖文本")
                            }
                        }
                    }
                }
                PdfInteractionMode.IN_PLACE_EDIT -> {
                    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("点击页面文字直接修改 (支持字体回退)", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { viewModel.switchMode(PdfInteractionMode.VIEW) }) {
                                Text("完成")
                            }
                        }
                    }
                }
                PdfInteractionMode.TRUE_REDACTION -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已标记 ${uiState.selectedRedactRegions.size} 处脱敏区域",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = uiState.selectedRedactRegions.isNotEmpty(),
                                onClick = { viewModel.commitTrueRedaction() }
                            ) {
                                Text("物理彻底擦除")
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (uiState.isNightMode) Color(0xFF121212) else Color(0xFFE0E0E0))
        ) {
            PdfInteractiveCanvas(
                uiState = uiState,
                onRegionSelected = { regionPt ->
                    if (uiState.interactionMode == PdfInteractionMode.OVERLAY_EDIT) {
                        viewModel.updateOverlayDraftBounds(regionPt)
                    } else if (uiState.interactionMode == PdfInteractionMode.TRUE_REDACTION) {
                        viewModel.addRedactionRegion(regionPt)
                    }
                },
                onObjectClicked = { obj ->
                    if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT && obj is PdfTextObject) {
                        viewModel.selectTextObjectForInPlaceEdit(obj)
                    }
                }
            )

            // 任务书强制红线警告 Banner
            if (uiState.overlayBannerWarningVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "当前为视觉覆盖模式，底层原始文字并未从文件结构中物理清除；如需彻底抹除敏感信息，请使用【安全脱敏 (Redaction)】功能。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissOverlayWarning() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }

            // 一级覆盖弹窗
            if (showOverlayDraftDialog) {
                OverlayDraftInputDialog(
                    initialDraft = uiState.activeOverlayDraft ?: OverlayEditDraft(RectF()),
                    onDismiss = { showOverlayDraftDialog = false },
                    onConfirm = { text, fontSize, textColor, maskColor ->
                        viewModel.updateOverlayDraftText(text, fontSize, textColor, maskColor)
                        viewModel.commitOverlayEdit()
                        showOverlayDraftDialog = false
                    }
                )
            }

            // 二级原位编辑弹窗
            uiState.activeInPlaceDraft?.let { inPlaceDraft ->
                AlertDialog(
                    onDismissRequest = { /* 取消 */ },
                    title = { Text("原位修改文字对象 (二级策略)") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("原始文本: \"${inPlaceDraft.initialText}\"", fontSize = 12.sp, color = Color.Gray)
                            OutlinedTextField(
                                value = inPlaceDraft.currentText,
                                onValueChange = { viewModel.updateInPlaceText(it) },
                                label = { Text("修改后文本") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("若原嵌入字体缺少新增汉字字符，系统将自动回退绑定内置开源思源中文字体。", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.commitInPlaceEdit() }) {
                            Text("确认原位替换")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.switchMode(PdfInteractionMode.VIEW) }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PdfInteractiveCanvas(
    uiState: PdfViewerUiState,
    onRegionSelected: (RectF) -> Unit,
    onObjectClicked: (com.openofficepdf.core.model.pdf.PdfObjectModel) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uiState.interactionMode) {
                if (uiState.interactionMode == PdfInteractionMode.VIEW) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                        offset += pan
                    }
                } else if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) {
                    detectTapGestures { tapOffset ->
                        // 点击命中测试 (Hit-testing)
                        for (obj in uiState.pageObjects) {
                            val screenRect = NormalizedCoordinatesTransformer.normalizedPtToScreen(
                                boundsPt = obj.boundsPt,
                                scale = scale,
                                viewportOffsetX = offset.x,
                                viewportOffsetY = offset.y
                            )
                            if (screenRect.contains(tapOffset.x, tapOffset.y)) {
                                onObjectClicked(obj)
                                break
                            }
                        }
                    }
                } else if (uiState.interactionMode == PdfInteractionMode.OVERLAY_EDIT ||
                           uiState.interactionMode == PdfInteractionMode.TRUE_REDACTION) {
                    detectDragGestures(
                        onDragStart = { start ->
                            dragStart = start
                            dragEnd = start
                        },
                        onDrag = { change, _ ->
                            dragEnd = change.position
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragEnd
                            if (start != null && end != null) {
                                val left = minOf(start.x, end.x)
                                val top = minOf(start.y, end.y)
                                val right = maxOf(start.x, end.x)
                                val bottom = maxOf(start.y, end.y)

                                val ptRect = NormalizedCoordinatesTransformer.screenToNormalizedPt(
                                    screenX = left,
                                    screenY = top,
                                    screenWidth = right - left,
                                    screenHeight = bottom - top,
                                    scale = scale,
                                    viewportOffsetX = offset.x,
                                    viewportOffsetY = offset.y
                                )
                                onRegionSelected(ptRect)
                            }
                            dragStart = null
                            dragEnd = null
                        }
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pageWidthPx = 595.276f * scale
            val pageHeightPx = 841.89f * scale
            val pageLeft = offset.x
            val pageTop = offset.y

            // 绘制页面白底
            drawRect(
                color = if (uiState.isNightMode) Color(0xFF1E1E1E) else Color.White,
                topLeft = Offset(pageLeft, pageTop),
                size = Size(pageWidthPx, pageHeightPx)
            )

            // 绘制页面原生文字对象高亮框 (原位编辑模式下可点击)
            for (obj in uiState.pageObjects) {
                val screenRect = NormalizedCoordinatesTransformer.normalizedPtToScreen(
                    boundsPt = obj.boundsPt,
                    scale = scale,
                    viewportOffsetX = offset.x,
                    viewportOffsetY = offset.y
                )
                val highlightColor = if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) {
                    Color.Green.copy(alpha = 0.25f)
                } else {
                    Color.LightGray.copy(alpha = 0.2f)
                }
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(screenRect.left, screenRect.top),
                    size = Size(screenRect.width(), screenRect.height())
                )
            }

            // 绘制已标记的真 Redaction 脱敏区域 (物理黑框脱敏标示)
            for (reg in uiState.selectedRedactRegions) {
                val sRect = NormalizedCoordinatesTransformer.normalizedPtToScreen(
                    boundsPt = reg,
                    scale = scale,
                    viewportOffsetX = offset.x,
                    viewportOffsetY = offset.y
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    topLeft = Offset(sRect.left, sRect.top),
                    size = Size(sRect.width(), sRect.height())
                )
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(sRect.left, sRect.top),
                    size = Size(sRect.width(), sRect.height()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 绘制用户当前手势框选中的临时矩形
            val start = dragStart
            val end = dragEnd
            if (start != null && end != null) {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val width = kotlin.math.abs(end.x - start.x)
                val height = kotlin.math.abs(end.y - start.y)

                val boxColor = if (uiState.interactionMode == PdfInteractionMode.TRUE_REDACTION) Color.Red else Color.Blue
                drawRect(
                    color = boxColor.copy(alpha = 0.2f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun OverlayDraftInputDialog(
    initialDraft: OverlayEditDraft,
    onDismiss: () -> Unit,
    onConfirm: (text: String, fontSize: Float, textColor: Int, maskColor: Int) -> Unit
) {
    var text by remember { mutableStateOf(initialDraft.text) }
    var fontSize by remember { mutableFloatStateOf(initialDraft.fontSizePt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入覆盖文本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("新文本内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("字号: ${fontSize.toInt()} pt")
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 8f..36f,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text, fontSize, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()) }
            ) {
                Text("应用覆盖")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
