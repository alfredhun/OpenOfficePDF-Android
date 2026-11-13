package com.openofficepdf.app

import android.content.Context
import androidx.room.Room
import com.openofficepdf.core.database.AppDatabase
import com.openofficepdf.core.memory.BitmapPool
import com.openofficepdf.core.memory.BitmapPoolImpl
import com.openofficepdf.core.storage.DocumentAccessProvider
import com.openofficepdf.core.storage.DocumentAccessProviderImpl
import com.openofficepdf.core.storage.TemporaryFileManager
import com.openofficepdf.core.storage.TransactionalFileCommitter
import com.openofficepdf.engine.converter.ConversionEngine
import com.openofficepdf.engine.converter.ConversionEngineImpl
import com.openofficepdf.engine.markdown.MarkdownAstParser
import com.openofficepdf.engine.markdown.MarkdownScrollSyncAndSanitizer
import com.openofficepdf.engine.markdown.MermaidRenderPool
import com.openofficepdf.engine.ocr.OcrEngine
import com.openofficepdf.engine.ocr.OcrEngineImpl
import com.openofficepdf.engine.ocr.SearchablePdfSynthesizer
import com.openofficepdf.engine.office.CollaboraOfficeAdapter
import com.openofficepdf.engine.office.OfficeEngine
import com.openofficepdf.engine.pdf.*

/**
 * 集中依赖注入容器 (遵循 Clean Architecture 与单一真理源原则)
 */
class AppContainer(context: Context) {

    // 数据库层 (CursorWindow 64KB 防爆保护)
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "openofficepdf.db"
    ).fallbackToDestructiveMigration().build()

    // 内存与存储基础设施
    val bitmapPool: BitmapPool = BitmapPoolImpl()
    val tempFileManager: TemporaryFileManager = TemporaryFileManager(context)
    val storageProvider: DocumentAccessProvider = DocumentAccessProviderImpl(context)
    val transactionalCommitter: TransactionalFileCommitter = TransactionalFileCommitter(context, tempFileManager)

    // PDF 引擎层
    val androidPdfRendererBridge: AndroidPdfRendererBridge = AndroidPdfRendererBridge(context)
    val pdfFontManager: PdfFontManager = PdfFontManager()
    val pdfRedactionService: RedactionService = RedactionService()
    val pdfTileRenderer: PdfTileRenderer = PdfTileRenderer(bitmapPool, androidPdfRendererBridge)
    val pdfEditEngine: PdfEditEngine = PdfEditEngineImpl(pdfFontManager, pdfRedactionService)

    // Office 引擎层 (跨进程驱动)
    val officeEngine: OfficeEngine = CollaboraOfficeAdapter(
        storageProvider = storageProvider,
        tempFileManager = tempFileManager,
        committer = transactionalCommitter,
        sessionDao = database.sessionAndEditDao()
    )

    // Markdown 引擎层
    val markdownAstParser: MarkdownAstParser = MarkdownAstParser()
    val markdownScrollSync: MarkdownScrollSyncAndSanitizer = MarkdownScrollSyncAndSanitizer()
    val mermaidPool: MermaidRenderPool = MermaidRenderPool(context)

    // OCR 引擎层
    val searchablePdfSynthesizer: SearchablePdfSynthesizer = SearchablePdfSynthesizer(context)
    val ocrEngine: OcrEngine = OcrEngineImpl(
        taskDao = database.taskDao(),
        context = context,
        searchablePdfSynthesizer = searchablePdfSynthesizer
    )

    // 格式转换引擎层
    val conversionEngine: ConversionEngine = ConversionEngineImpl(
        taskDao = database.taskDao(),
        context = context
    )
}
