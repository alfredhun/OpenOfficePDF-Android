package com.openofficepdf.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * 真实双层可搜索 PDF 合成器与图像分析工具
 * 底层利用 Android 原生 PdfDocument 实现：
 * 1. 底层：绘制原图（高清原貌）
 * 2. 顶层：以透明色 Paint (Color.TRANSPARENT) 绘制 OCR 识别得到的文字，保证可选中、可长按复制、可搜索
 */
class SearchablePdfSynthesizer(
    private val context: Context
) {
    suspend fun synthesize(
        sourceUri: Uri,
        ocrPages: List<OcrPageResult>,
        outputUri: Uri
    ): EngineResult<Unit> = withContext(AppDispatchers.io) {
        try {
            val pdfDocument = PdfDocument()

            // 判断输入是 PDF 还是普通图片
            val mimeType = context.contentResolver.getType(sourceUri) ?: ""
            val isPdfSource = mimeType.contains("pdf") || sourceUri.toString().endsWith(".pdf", ignoreCase = true)

            if (isPdfSource) {
                context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    for (ocrPage in ocrPages) {
                        val pageIdx = ocrPage.pageIndex
                        if (pageIdx < 0 || pageIdx >= renderer.pageCount) continue
                        val rendererPage = renderer.openPage(pageIdx)

                        val pageInfo = PdfDocument.PageInfo.Builder(rendererPage.width, rendererPage.height, pageIdx + 1).create()
                        val documentPage = pdfDocument.startPage(pageInfo)
                        val canvas = documentPage.canvas

                        // 1. 渲染原 PDF 页面到底层 Canvas
                        val pageBitmap = Bitmap.createBitmap(rendererPage.width, rendererPage.height, Bitmap.Config.ARGB_8888)
                        pageBitmap.eraseColor(Color.WHITE)
                        rendererPage.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        canvas.drawBitmap(pageBitmap, 0f, 0f, null)
                        rendererPage.close()
                        pageBitmap.recycle()

                        // 2. 绘制透明文字层
                        drawInvisibleTextLayer(canvas, ocrPage)

                        pdfDocument.finishPage(documentPage)
                    }
                    renderer.close()
                }
            } else {
                // 普通图片来源
                val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext EngineResult.failure(EngineErrorCode.FILE_READ_FAILED, "无法读取图片来源")

                for (ocrPage in ocrPages) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, ocrPage.pageIndex + 1).create()
                    val documentPage = pdfDocument.startPage(pageInfo)
                    val canvas = documentPage.canvas

                    // 1. 画原图
                    canvas.drawBitmap(bitmap, 0f, 0f, null)

                    // 2. 画透明文字
                    drawInvisibleTextLayer(canvas, ocrPage)

                    pdfDocument.finishPage(documentPage)
                }
                bitmap.recycle()
            }

            // 输出到目标文件
            context.contentResolver.openOutputStream(outputUri, "wt")?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()

            EngineResult.success(Unit)
        } catch (e: Exception) {
            EngineResult.failure(EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED, "合成双层 PDF 失败: ${e.message}")
        }
    }

    private fun drawInvisibleTextLayer(canvas: Canvas, ocrPage: OcrPageResult) {
        val paint = Paint().apply {
            color = Color.TRANSPARENT // 透明文字
            isAntiAlias = true
        }

        for (textBlock in ocrPage.textBlocks) {
            for (charBox in textBlock.characters) {
                paint.textSize = (charBox.bounds.height() * 0.9f).coerceAtLeast(10f)
                canvas.drawText(charBox.char, charBox.bounds.left, charBox.bounds.bottom, paint)
            }
        }
    }
}
