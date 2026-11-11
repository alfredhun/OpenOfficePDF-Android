package com.openofficepdf.engine.pdf

import android.content.Context
import com.openofficepdf.core.model.pdf.PdfFontDescriptor
import java.io.File

/**
 * PDF 字体子集与回退嵌入管理器 (任务书 2.1.2 节 & 8.2.3 节)
 */
class PdfFontManager(private val context: Context) {

    companion object {
        const val DEFAULT_FALLBACK_FONT = "SourceHanSansSC-Regular.otf"
    }

    /**
     * 检测原文字对象的字体是否能安全容纳新输入的字符
     */
    fun canFontEncodeNewText(fontInfo: PdfFontDescriptor, newText: String): Boolean {
        // 如果是子集字体 (Subset Font, 常见标识如 ABCDEF+SimSun)，默认新字符很大概率不在原字体表中
        if (fontInfo.isSubset) {
            return false
        }
        return fontInfo.canEncodeNewChars
    }

    /**
     * 获取内置开源中文字体文件供子集化重嵌入
     */
    fun getFallbackFontFile(): File {
        val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val fontFile = File(fontsDir, DEFAULT_FALLBACK_FONT)
        if (!fontFile.exists()) {
            // 从 assets/fonts/ 拷贝内置思源黑体基础字库
            context.assets.open("fonts/$DEFAULT_FALLBACK_FONT").use { input ->
                fontFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return fontFile
    }
}
