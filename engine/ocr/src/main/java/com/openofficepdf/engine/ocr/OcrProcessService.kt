package com.openofficepdf.engine.ocr

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.openofficepdf.core.ipc.IOcrProcessBridge

/**
 * 运行于 :ocr_engine 独立进程的 Service (任务书 3.2 节)
 */
class OcrProcessService : Service() {

    private val binder = object : IOcrProcessBridge.Stub() {
        override fun startOcrTask(
            taskId: String?,
            inputPfd: ParcelFileDescriptor?,
            startPage: Int,
            endPage: Int,
            enableLayout: Boolean,
            outputJsonDirPath: String?
        ) {}

        override fun cancelTask(taskId: String?) {}

        override fun ping(): Boolean = true
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
