package com.openofficepdf.feature.pdf

import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openofficepdf.core.model.pdf.PdfTextObject
import com.openofficepdf.engine.pdf.NormalizedCoordinatesTransformer

/**
 * PDF 交互阅读器与三级编辑画板 (Jetpack Compose 原生构建)
 * - 真实 PDF 渲染：支持打开手机本地任意 PDF 并通过 AndroidPdfRenderer 硬件加速光栅化呈现
 * - 移动端响应式全屏铺满与缩放平移 (Fit Width / Fit Page)
 * - 一级策略：覆盖式编辑 (带未清除红线预警)
 * - 二级策略：内容流对象级原位编辑 (点选文字对象 -> 原位修改 -> 实时重新排版)
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

    // SAF 打开本地 PDF 文件
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "文档.pdf"
            viewModel.openPdfDocument(it, fileName)
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
                        Text(uiState.documentTitle, fontSize = 16.sp, maxLines = 1)
                        if (uiState.pageCount > 1) {
                            Text("第 ${uiState.currentPageIndex + 1} / ${uiState.pageCount} 页", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "打开 PDF")
                    }
                    IconButton(onClick = { viewModel.toggleNightMode() }) {
                        Icon(
                            imageVector = if (uiState.isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "夜间模式"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // 悬浮圆角胶囊模式切换工具栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.switchMode(PdfInteractionMode.VIEW) }) {
                                Icon(
                                    Icons.Default.PanTool,
                                    contentDescription = "浏览模式",
                                    tint = if (uiState.interactionMode == PdfInteractionMode.VIEW) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                            IconButton(onClick = { viewModel.switchMode(PdfInteractionMode.IN_PLACE_EDIT) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "原位编辑",
                                    tint = if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                            IconButton(onClick = { viewModel.switchMode(PdfInteractionMode.OVERLAY_EDIT) }) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = "覆盖编辑",
                                    tint = if (uiState.interactionMode == PdfInteractionMode.OVERLAY_EDIT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                            IconButton(onClick = { viewModel.switchMode(PdfInteractionMode.TRUE_REDACTION) }) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = "真脱敏抹除",
                                    tint = if (uiState.interactionMode == PdfInteractionMode.TRUE_REDACTION) MaterialTheme.colorScheme.error else LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                // 翻页与辅助控制栏
                if (uiState.pageCount > 1) {
                    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = uiState.currentPageIndex > 0,
                                onClick = { viewModel.prevPage() }
                            ) {
                                Text("上一页")
                            }
                            Text("第 ${uiState.currentPageIndex + 1} 页 / 共 ${uiState.pageCount} 页", fontSize = 13.sp)
                            TextButton(
                                enabled = uiState.currentPageIndex + 1 < uiState.pageCount,
                                onClick = { viewModel.nextPage() }
                            ) {
                                Text("下一页")
                            }
                        }
                    }
                }

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
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "安全合规警示：覆盖式编辑仅在视觉表面遮盖，底层原始内容流文字依然可被复制或提取。机密信息脱敏请务必使用“真脱敏抹除”功能！",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissOverlayWarning() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭提示")
                        }
                    }
                }
            }

            // 覆盖编辑输入弹窗
            if (showOverlayDraftDialog && uiState.activeOverlayDraft != null) {
                OverlayDraftInputDialog(
                    initialDraft = uiState.activeOverlayDraft!!,
                    onDismiss = { showOverlayDraftDialog = false },
                    onConfirm = { text, fontSize, textColor, maskColor ->
                        viewModel.commitOverlayEdit()
                        showOverlayDraftDialog = false
                    }
                )
            }

            // 二级原位编辑弹窗
            uiState.activeInPlaceDraft?.let { draft ->
                AlertDialog(
                    onDismissRequest = { viewModel.switchMode(PdfInteractionMode.VIEW) },
                    title = { Text("原位修改文字内容") },
                    text = {
                        Column {
                            Text("原始文本: ${draft.initialText}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = draft.currentText,
                                onValueChange = { viewModel.updateInPlaceEditText(it) },
                                label = { Text("修改为") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            draft.fontFallbackWarning?.let { w ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(w, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uiState.interactionMode) {
                if (uiState.interactionMode == PdfInteractionMode.VIEW) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                        offset += pan
                    }
                } else if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) {
                    detectTapGestures { tapOffset ->
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
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // 核心优化：自适应铺满屏幕计算 (Fit Width)
        val rawW = if (uiState.rawPageWidthPt > 0) uiState.rawPageWidthPt else 595f
        val rawH = if (uiState.rawPageHeightPt > 0) uiState.rawPageHeightPt else 842f

        // 计算让 PDF 完美占满屏幕宽度的自适应比例
        val fitWidthScale = if (rawW > 0) screenWidthPx / rawW else 1.0f

        // 页面在屏幕上的实际绘制宽高
        val effectiveScale = scale * fitWidthScale
        val pageWidthPx = rawW * effectiveScale
        val pageHeightPx = rawH * effectiveScale

        // 默认让页面在屏幕水平居中
        val pageLeft = offset.x + if (pageWidthPx < screenWidthPx) (screenWidthPx - pageWidthPx) / 2f else 0f
        val pageTop = offset.y + if (pageHeightPx < screenHeightPx) (screenHeightPx - pageHeightPx) / 2f else 0f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val bitmap = uiState.renderedBitmap

            if (bitmap != null && !bitmap.isRecycled) {
                // 绘制真实渲染得到的 PDF 页面位图 (铺满视口)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(pageLeft.toInt(), pageTop.toInt()),
                    dstSize = IntSize(pageWidthPx.toInt(), pageHeightPx.toInt())
                )
            } else {
                // 绘制页面白底
                drawRect(
                    color = if (uiState.isNightMode) Color(0xFF1E1E1E) else Color.White,
                    topLeft = Offset(pageLeft, pageTop),
                    size = Size(pageWidthPx, pageHeightPx)
                )
            }

            // 绘制页面文字对象高亮框与原位编辑状态
            for (obj in uiState.pageObjects) {
                val screenRect = NormalizedCoordinatesTransformer.normalizedPtToScreen(
                    boundsPt = obj.boundsPt,
                    scale = effectiveScale,
                    viewportOffsetX = pageLeft,
                    viewportOffsetY = pageTop
                )
                val highlightColor = if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) {
                    Color(0xFF4CAF50).copy(alpha = 0.35f)
                } else {
                    Color.LightGray.copy(alpha = 0.15f)
                }
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(screenRect.left, screenRect.top),
                    size = Size(screenRect.width(), screenRect.height())
                )
                if (uiState.interactionMode == PdfInteractionMode.IN_PLACE_EDIT) {
                    drawRect(
                        color = Color(0xFF2E7D32),
                        topLeft = Offset(screenRect.left, screenRect.top),
                        size = Size(screenRect.width(), screenRect.height()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // 绘制已标记的真 Redaction 脱敏区域
            for (reg in uiState.selectedRedactRegions) {
                val sRect = NormalizedCoordinatesTransformer.normalizedPtToScreen(
                    boundsPt = reg,
                    scale = effectiveScale,
                    viewportOffsetX = pageLeft,
                    viewportOffsetY = pageTop
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.85f),
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
