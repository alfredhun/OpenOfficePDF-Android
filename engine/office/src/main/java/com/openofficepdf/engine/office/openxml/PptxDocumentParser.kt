package com.openofficepdf.engine.office.openxml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 纯离线纯 Kotlin/Java 实现的 PPT (.pptx) 解析器 (基于 OpenXML ECMA-376)
 * 提取每页幻灯片 (ppt/slides/slide*.xml) 中的真实文本与段落，以及尺寸
 */
object PptxDocumentParser {

    fun parse(inputStream: InputStream, title: String = "演示文稿.pptx"): ParsedPptxDocument {
        val entries = mutableMapOf<String, ByteArray>()
        val zip = ZipInputStream(inputStream)
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name == "ppt/presentation.xml" || name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                entries[name] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        // 1. 解析幻灯片尺寸比例
        val (widthPt, heightPt) = parsePresentationDimensions(entries["ppt/presentation.xml"])

        // 2. 按顺序提取每页幻灯片内容
        val slideKeys = entries.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { key ->
                // 按数字排序 slide1, slide2, slide10
                key.filter { it.isDigit() }.toIntOrNull() ?: 0
            }

        val slides = mutableListOf<PptxSlideContent>()
        slideKeys.forEachIndexed { index, key ->
            val slideBytes = entries[key] ?: return@forEachIndexed
            val content = parseSlide(slideBytes, defaultTitle = "幻灯片 ${index + 1}")
            slides.add(content)
        }

        if (slides.isEmpty()) {
            slides.add(PptxSlideContent(title = "空白演示文稿", paragraphs = listOf("未检测到幻灯片内容")))
        }

        return ParsedPptxDocument(
            title = title,
            slideWidthPt = widthPt,
            slideHeightPt = heightPt,
            slides = slides
        )
    }

    private fun parsePresentationDimensions(bytes: ByteArray?): Pair<Float, Float> {
        if (bytes == null) return 960f to 540f // 默认 16:9
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sldSz") {
                val cxEmu = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 9144000f
                val cyEmu = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 5143500f
                // 1 pt = 12700 EMU
                return (cxEmu / 12700f) to (cyEmu / 12700f)
            }
            eventType = parser.next()
        }
        return 960f to 540f
    }

    private fun parseSlide(bytes: ByteArray, defaultTitle: String): PptxSlideContent {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        val paragraphs = mutableListOf<String>()
        var eventType = parser.eventType
        var currentP = StringBuilder()
        var insideP = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "p") {
                        insideP = true
                        currentP.clear()
                    } else if (insideP && parser.name == "t") {
                        currentP.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p") {
                        val text = currentP.toString().trim()
                        if (text.isNotEmpty()) {
                            paragraphs.add(text)
                        }
                        insideP = false
                    }
                }
            }
            eventType = parser.next()
        }

        val title = paragraphs.firstOrNull() ?: defaultTitle
        val bodyParagraphs = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()

        return PptxSlideContent(
            title = title,
            paragraphs = bodyParagraphs
        )
    }
}
