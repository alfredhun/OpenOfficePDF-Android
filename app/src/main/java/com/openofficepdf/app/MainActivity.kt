package com.openofficepdf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as OpenOfficeApp).appContainer

        setContent {
            MaterialTheme {
                Surface {
                    var currentRoute by remember { mutableStateOf("home") }

                    when (currentRoute) {
                        "settings" -> {
                            SettingsScreen(onBack = { currentRoute = "home" })
                        }
                        "pdf" -> {
                            val pdfViewModel = remember {
                                PdfViewerViewModel(
                                    tileRenderer = appContainer.pdfTileRenderer,
                                    editEngine = appContainer.pdfEditEngine,
                                    sessionDao = appContainer.database.sessionAndEditDao(),
                                    storageProvider = appContainer.storageProvider,
                                    tempFileManager = appContainer.tempFileManager,
                                    transactionalCommitter = appContainer.transactionalCommitter
                                )
                            }
                            PdfViewerScreen(
                                viewModel = pdfViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "word" -> {
                            val wordViewModel = remember {
                                WordWorkspaceViewModel(officeEngine = appContainer.officeEngine)
                            }
                            WordWorkspaceScreen(
                                viewModel = wordViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "excel" -> {
                            val excelViewModel = remember {
                                ExcelWorkspaceViewModel(officeEngine = appContainer.officeEngine)
                            }
                            ExcelWorkspaceScreen(
                                viewModel = excelViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "ppt" -> {
                            val pptViewModel = remember {
                                PresentationViewModel(officeEngine = appContainer.officeEngine)
                            }
                            PresentationScreen(
                                viewModel = pptViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "markdown" -> {
                            val mdViewModel = remember {
                                MarkdownWorkspaceViewModel(
                                    astParser = appContainer.markdownAstParser,
                                    scrollSync = appContainer.markdownScrollSync,
                                    mermaidPool = appContainer.mermaidPool,
                                    documentDao = appContainer.database.documentDao(),
                                    storageProvider = appContainer.storageProvider,
                                    tempFileManager = appContainer.tempFileManager,
                                    transactionalCommitter = appContainer.transactionalCommitter
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
                                            - 本地公式重算与 PP-OCR
                                            
                                            ```kotlin
                                            fun verifyOffline(): Boolean = true
                                            ```
                                        """.trimIndent()
                                    )
                                }
                            }
                            MarkdownWorkspaceScreen(
                                viewModel = mdViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "ocr" -> {
                            val ocrViewModel = remember {
                                OcrViewModel(
                                    ocrEngine = appContainer.ocrEngine,
                                    taskDao = appContainer.database.taskDao()
                                )
                            }
                            OcrScreen(
                                viewModel = ocrViewModel,
                                onNavigateBack = { currentRoute = "home" }
                            )
                        }
                        "converter" -> {
                            val convViewModel = remember {
                                ConverterViewModel(
                                    conversionEngine = appContainer.conversionEngine,
                                    taskDao = appContainer.database.taskDao()
                                )
                            }
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
