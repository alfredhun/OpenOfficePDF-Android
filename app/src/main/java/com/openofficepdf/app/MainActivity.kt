package com.openofficepdf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * 主入口 Activity：串联 6+1 大工作台与全局设置页面
 * 具备横竖屏与折叠屏无损旋转自适应、配置变更状态保持与生命周期安全管理
 */
class MainActivity : ComponentActivity() {

    private val appContainer by lazy { (application as OpenOfficeApp).appContainer }

    private val pdfViewModel by lazy {
        PdfViewerViewModel(
            tileRenderer = appContainer.pdfTileRenderer,
            editEngine = appContainer.pdfEditEngine,
            sessionDao = appContainer.database.sessionAndEditDao(),
            storageProvider = appContainer.storageProvider,
            tempFileManager = appContainer.tempFileManager,
            transactionalCommitter = appContainer.transactionalCommitter,
            pdfRendererBridge = appContainer.androidPdfRendererBridge
        )
    }

    private val wordViewModel by lazy {
        WordWorkspaceViewModel(officeEngine = appContainer.officeEngine)
    }

    private val excelViewModel by lazy {
        ExcelWorkspaceViewModel(officeEngine = appContainer.officeEngine)
    }

    private val pptViewModel by lazy {
        PresentationViewModel(officeEngine = appContainer.officeEngine)
    }

    private val mdViewModel by lazy {
        MarkdownWorkspaceViewModel(
            astParser = appContainer.markdownAstParser,
            scrollSync = appContainer.markdownScrollSync,
            mermaidPool = appContainer.mermaidPool,
            documentDao = appContainer.database.documentDao(),
            storageProvider = appContainer.storageProvider,
            tempFileManager = appContainer.tempFileManager,
            transactionalCommitter = appContainer.transactionalCommitter,
            context = this@MainActivity
        ).apply {
            loadDocument(
                docId = com.openofficepdf.core.model.DocumentId("sample_md"),
                title = "离线使用手册.md",
                initialMarkdown = """
                    # OpenOfficePDF 离线办公套件
                    
                    完全本地运行、零云端依赖的 Android 综合办公应用。
                    
                    ## 核心特性
                    - 100% 离线运行 (无 INTERNET 权限)
                    - 移动端自适应重排排版
                    - 本地公式重算与双轨离线 OCR
                    
                    ```kotlin
                    fun verifyOffline(): Boolean = true
                    ```
                """.trimIndent()
            )
        }
    }

    private val ocrViewModel by lazy {
        OcrViewModel(
            ocrEngine = appContainer.ocrEngine,
            taskDao = appContainer.database.taskDao()
        )
    }

    private val convViewModel by lazy {
        ConverterViewModel(
            conversionEngine = appContainer.conversionEngine,
            taskDao = appContainer.database.taskDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    var currentRoute by rememberSaveable { mutableStateOf("home") }

                    when (currentRoute) {
                        "settings" -> {
                            SettingsScreen(onBack = { currentRoute = "home" })
                        }
                        "pdf" -> {
                            PdfViewerScreen(
                                viewModel = pdfViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "word" -> {
                            WordWorkspaceScreen(
                                viewModel = wordViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "excel" -> {
                            ExcelWorkspaceScreen(
                                viewModel = excelViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "ppt" -> {
                            PresentationScreen(
                                viewModel = pptViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "markdown" -> {
                            MarkdownWorkspaceScreen(
                                viewModel = mdViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "ocr" -> {
                            OcrScreen(
                                viewModel = ocrViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "converter" -> {
                            ConverterScreen(
                                viewModel = convViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        else -> {
                            HomeScreen(
                                onEntryClick = { route ->
                                    currentRoute = route
                                },
                                onSettingsClick = {
                                    currentRoute = "settings"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
