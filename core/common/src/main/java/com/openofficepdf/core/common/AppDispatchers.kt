package com.openofficepdf.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * 集中管理的协程调度器
 * 包含 Native 操作专用的单线程调度器（防止 FPDF_DOCUMENT 跨线程竞态）
 */
object AppDispatchers {
    val main: CoroutineDispatcher = Dispatchers.Main
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
    
    // PDFium 专用受控 Native 单线程调度器 (任务书 3.2 节)
    val pdfNative: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "PdfiumNativeWorker").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY + 1
            }
        }.asCoroutineDispatcher()
    }
}
