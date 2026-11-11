package com.openofficepdf.engine.pdf

import android.graphics.Bitmap

/**
 * PDFium Native 绑定桥接类
 */
object PdfiumNativeBridge {

    init {
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("pdfium_bridge")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            // 在纯 JVM 单元测试环境中优雅容错
        }
    }

    external fun nativeInit(): Boolean
    external fun nativeDestroy()

    external fun nativeOpenDocumentWithFd(fd: Int, size: Long, password: String?): Long
    external fun nativeCloseDocument(docHandle: Long)

    external fun nativeGetPageCount(docHandle: Long): Int
    external fun nativeGetPageInfo(docHandle: Long, pageIndex: Int): FloatArray

    external fun nativeRenderTile(
        docHandle: Long,
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        tileWidthPx: Int,
        tileHeightPx: Int,
        nightMode: Boolean,
        targetBitmap: Bitmap
    ): Boolean

    external fun nativeUpdateTextObject(
        docHandle: Long,
        pageIndex: Int,
        objectIndex: Int,
        newText: String,
        newFontSizePt: Float
    ): Boolean

    external fun nativeApplyTrueRedaction(
        docHandle: Long,
        pageIndex: Int,
        regionsFlat: FloatArray
    ): Int
}
