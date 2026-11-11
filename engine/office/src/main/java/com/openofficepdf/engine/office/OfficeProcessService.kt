package com.openofficepdf.engine.office

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.openofficepdf.core.ipc.IOfficeProcessBridge

/**
 * 运行于 :office_engine 独立进程的 Service (任务书 3.2 节)
 */
class OfficeProcessService : Service() {

    private val binder = object : IOfficeProcessBridge.Stub() {
        override fun openDocumentFd(pfd: ParcelFileDescriptor?, format: String?): Int {
            return 1 // 模拟 handle
        }

        override fun setMobileReflowMode(docHandle: Int, enabled: Boolean) {}

        override fun dispatchKeyEvent(docHandle: Int, keyCode: Int, unicodeChar: Int) {}

        override fun executeUnoCommand(docHandle: Int, command: String?, paramsJson: String?) {}

        override fun exportToPdf(docHandle: Int, outputPfd: ParcelFileDescriptor?): Boolean {
            return true
        }

        override fun closeDocument(docHandle: Int) {}

        override fun ping(): Boolean = true
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
