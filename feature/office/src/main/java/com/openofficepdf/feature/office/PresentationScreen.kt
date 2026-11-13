package com.openofficepdf.feature.office

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openofficepdf.engine.office.SlidePage

/**
 * PPT 幻灯片放映与批注阅读器界面 (任务书拓展)
 * - 纯净只读高保真查看与全屏放映
 * - 原格式能力保护：不开放易产生排版破坏的对象级文本框/形状编辑
 * - 荧光手写批注与墨迹擦除层
 * - 底部缩略图流式横滑导航
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationScreen(
    viewModel: PresentationViewModel,
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

    val currentSlide = uiState.slides.getOrNull(uiState.currentSlideIndex)
        ?: SlidePage(0, "无内容")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!uiState.isFullscreenPlaying) {
                TopAppBar(
                    title = {
                        Column {
                            Text(uiState.documentTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "第 ${uiState.currentSlideIndex + 1} / ${uiState.slides.size} 页 (高保真放映模式)",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
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
                                val title = uri.lastPathSegment?.substringAfterLast('/') ?: "演示文稿.pptx"
                                viewModel.openPresentation(com.openofficepdf.core.model.DocumentId(uri.toString()), title, uri)
                            }
                        }

                        IconButton(onClick = { launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/vnd.ms-powerpoint", "*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "打开演示文稿")
                        }

                        // 批注画笔切换
                        IconButton(onClick = {
                            val nextMode = if (uiState.interactionMode == SlideInteractionMode.INK_PEN) {
                                SlideInteractionMode.VIEW
                            } else {
                                SlideInteractionMode.INK_PEN
                            }
                            viewModel.setInteractionMode(nextMode)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = "手写批注",
                                tint = if (uiState.interactionMode == SlideInteractionMode.INK_PEN) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        if (uiState.interactionMode == SlideInteractionMode.INK_PEN) {
                            IconButton(onClick = { viewModel.clearCurrentSlideStrokes() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "清空批注")
                            }
                        }

                        // 全屏放映按钮
                        IconButton(onClick = { viewModel.toggleFullscreenPlay() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "全屏放映")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!uiState.isFullscreenPlaying) {
                // 底部幻灯片缩略图横向导航栏
                Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(uiState.slides) { idx, slide ->
                                val isSelected = (idx == uiState.currentSlideIndex)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFD4D4D4)
                                    ),
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .clickable { viewModel.switchSlide(idx) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (uiState.isFullscreenPlaying) PaddingValues(0.dp) else innerPadding)
                .background(if (uiState.isFullscreenPlaying) Color.Black else Color(0xFFEBEBEB))
        ) {
            // 核心幻灯片画板与手势批注层
            SlideDisplaySurface(
                slide = currentSlide,
                interactionMode = uiState.interactionMode,
                inkColor = uiState.activeInkColor,
                onAddStroke = { points -> viewModel.addInkStroke(points) },
                onSlideClick = {
                    if (uiState.isFullscreenPlaying) {
                        viewModel.nextSlide()
                    }
                }
            )

            // 全屏放映时的退出放映轻量浮动按钮
            if (uiState.isFullscreenPlaying) {
                IconButton(
                    onClick = { viewModel.toggleFullscreenPlay() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
                }
            }
        }
    }
}

/**
 * 幻灯片内容渲染与触摸手写批注层
 */
@Composable
fun SlideDisplaySurface(
    slide: SlidePage,
    interactionMode: SlideInteractionMode,
    inkColor: Int,
    onAddStroke: (List<Pair<Float, Float>>) -> Unit,
    onSlideClick: () -> Unit
) {
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val slideAspect = 16f / 9f
        val containerAspect = if (maxHeight.value > 0) maxWidth / maxHeight else slideAspect
        val surfaceModifier = if (containerAspect > slideAspect) {
            Modifier.fillMaxHeight().aspectRatio(slideAspect)
        } else {
            Modifier.fillMaxWidth().aspectRatio(slideAspect)
        }

        // 16:9 标准幻灯片画板 (自适应横竖屏容器尺寸，彻底杜绝横屏约束崩溃)
        Surface(
            shape = RoundedCornerShape(4.dp),
            shadowElevation = 8.dp,
            color = Color.White,
            modifier = surfaceModifier
                .border(1.dp, Color(0xFFC0C0C0))
                .pointerInput(interactionMode) {
                    if (interactionMode == SlideInteractionMode.INK_PEN) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activePoints = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                activePoints = activePoints + change.position
                            },
                            onDragEnd = {
                                val size = this.size
                                if (activePoints.isNotEmpty() && size.width > 0 && size.height > 0) {
                                    val normalized = activePoints.map {
                                        (it.x / size.width) to (it.y / size.height)
                                    }
                                    onAddStroke(normalized)
                                }
                                activePoints = emptyList()
                            }
                        )
                    }
                }
                .clickable {
                    if (interactionMode == SlideInteractionMode.VIEW) {
                        onSlideClick()
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 幻灯片排版内容展示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = slide.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E1E)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    slide.notes?.let {
                        Text(
                            text = it,
                            fontSize = 15.sp,
                            color = Color.Gray
                        )
                    }
                }

                // 幻灯片上方覆盖的手写批注绘制层 (Canvas)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. 绘制历史保存的墨迹
                    for (stroke in slide.strokes) {
                        if (stroke.points.size >= 2) {
                            val path = Path()
                            val first = stroke.points[0]
                            path.moveTo(first.first * w, first.second * h)
                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                path.lineTo(pt.first * w, pt.second * h)
                            }
                            drawPath(
                                path = path,
                                color = Color(stroke.colorArgb),
                                style = Stroke(
                                    width = stroke.strokeWidthDp.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // 2. 绘制当前正在拖拽的墨迹
                    if (activePoints.size >= 2) {
                        val activePath = Path()
                        activePath.moveTo(activePoints[0].x, activePoints[0].y)
                        for (i in 1 until activePoints.size) {
                            activePath.lineTo(activePoints[i].x, activePoints[i].y)
                        }
                        drawPath(
                            path = activePath,
                            color = Color(inkColor),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}
