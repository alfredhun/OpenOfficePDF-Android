package com.openofficepdf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.feature.converter.ConverterScreen
import com.openofficepdf.feature.converter.ConverterViewModel
import com.openofficepdf.feature.home.HomeScreen
import com.openofficepdf.feature.markdown.MarkdownWorkspaceScreen
import com.openofficepdf.feature.markdown.MarkdownWorkspaceViewModel
import com.openofficepdf.feature.ocr.OcrScreen
import com.openofficepdf.feature.ocr.OcrViewModel
import com.openofficepdf.feature.office.*
import com.openofficepdf.feature.pdf.PdfViewerScreen
import com.openofficepdf.feature.pdf.PdfViewerViewModel
import com.openofficepdf.feature.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { (application as OpenOfficeApp).appContainer }

    // 统一 ViewModel 工厂，确保配置变更（横竖屏/折叠屏）时状态无损保留
    private val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(PdfViewerViewModel::class.java) -> {
                    PdfViewerViewModel(
                        tileRenderer = appContainer.pdfTileRenderer,
                        editEngine = appContainer.pdfEditEngine,
                        sessionDao = appContainer.database.sessionAndEditDao(),
                        storageProvider = appContainer.storageProvider,
                        tempFileManager = appContainer.tempFileManager,
                        transactionalCommitter = appContainer.transactionalCommitter,
                        pdfRendererBridge = appContainer.androidPdfRendererBridge
                    ) as T
                }
                modelClass.isAssignableFrom(WordWorkspaceViewModel::class.java) ->
                    WordWorkspaceViewModel(officeEngine = appContainer.officeEngine) as T
                modelClass.isAssignableFrom(ExcelWorkspaceViewModel::class.java) ->
                    ExcelWorkspaceViewModel(officeEngine = appContainer.officeEngine) as T
                modelClass.isAssignableFrom(PresentationViewModel::class.java) ->
                    PresentationViewModel(officeEngine = appContainer.officeEngine) as T
                modelClass.isAssignableFrom(MarkdownWorkspaceViewModel::class.java) ->
                    MarkdownWorkspaceViewModel(
                        astParser = appContainer.markdownAstParser,
                        scrollSync = appContainer.markdownScrollSync,
                        mermaidPool = appContainer.mermaidPool,
                        documentDao = appContainer.database.documentDao(),
                        storageProvider = appContainer.storageProvider,
                        tempFileManager = appContainer.tempFileManager,
                        transactionalCommitter = appContainer.transactionalCommitter,
                        // 修复：使用 applicationContext 杜绝 Activity 内存泄漏
                        context = applicationContext
                    ) as T
                modelClass.isAssignableFrom(OcrViewModel::class.java) ->
                    OcrViewModel(
                        ocrEngine = appContainer.ocrEngine,
                        taskDao = appContainer.database.taskDao()
                    ) as T
                modelClass.isAssignableFrom(ConverterViewModel::class.java) ->
                    ConverterViewModel(
                        conversionEngine = appContainer.conversionEngine,
                        taskDao = appContainer.database.taskDao()
                    ) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    private val pdfViewModel: PdfViewerViewModel by viewModels { viewModelFactory }
    private val wordViewModel: WordWorkspaceViewModel by viewModels { viewModelFactory }
    private val excelViewModel: ExcelWorkspaceViewModel by viewModels { viewModelFactory }
    private val pptViewModel: PresentationViewModel by viewModels { viewModelFactory }
    private val mdViewModel: MarkdownWorkspaceViewModel by viewModels { viewModelFactory }
    private val ocrViewModel: OcrViewModel by viewModels { viewModelFactory }
    private val convViewModel: ConverterViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 仅在首次冷启动时加载默认示例文档，避免旋转后覆盖用户编辑内容
        if (savedInstanceState == null) {
            mdViewModel.loadDocument(
                docId = DocumentId("sample_md"),
                title = "离线使用手册.md",
                initialMarkdown = """
                    # OpenOfficePDF 离线办公套件
                    
                    完全本地运行、零云端依赖的 Android 综合办公应用。
                    
                    ## 核心特性
                    - 100% 离线运行 (无 INTERNET 权限)
                    - 移动端自适应重排排版
                    - 本地公式重算与双轨离线 OCR
                """.trimIndent()
            )
        }

        setContent {
            MaterialTheme {
                Surface {
                    var currentRoute by rememberSaveable { mutableStateOf("home") }

                    // 修复：拦截子页面物理返回键，返回首页而非直接退出应用
                    BackHandler(enabled = currentRoute != "home") {
                        currentRoute = "home"
                    }

                    when (currentRoute) {
                        "settings" -> SettingsScreen(onBack = { currentRoute = "home" })
                        "pdf" -> PdfViewerScreen(viewModel = pdfViewModel, onNavigateBack = { currentRoute = "home" })
                        "word" -> WordWorkspaceScreen(viewModel = wordViewModel, onNavigateBack = { currentRoute = "home" })
                        "excel" -> ExcelWorkspaceScreen(viewModel = excelViewModel, onNavigateBack = { currentRoute = "home" })
                        "ppt" -> PresentationScreen(viewModel = pptViewModel, onNavigateBack = { currentRoute = "home" })
                        "markdown" -> MarkdownWorkspaceScreen(viewModel = mdViewModel, onNavigateBack = { currentRoute = "home" })
                        "ocr" -> OcrScreen(viewModel = ocrViewModel, onNavigateBack = { currentRoute = "home" })
                        "converter" -> ConverterScreen(viewModel = convViewModel, onNavigateBack = { currentRoute = "home" })
                        else -> HomeScreen(
                            onEntryClick = { route -> currentRoute = route },
                            onSettingsClick = { currentRoute = "settings" }
                        )
                    }
                }
            }
        }
    }
}
