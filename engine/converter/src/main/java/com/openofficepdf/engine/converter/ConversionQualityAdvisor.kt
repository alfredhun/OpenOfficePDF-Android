package com.openofficepdf.engine.converter

import com.openofficepdf.core.model.DocumentFormat

/**
 * 转换质量预检结果模型
 */
data class QualityAssessment(
    val tier: ConversionTier,
    val fidelityPercentageEstimate: Int, // 预估保真度 0% ~ 100%
    val titleWarning: String,
    val detailedNotice: String,
    val isOcrRecommended: Boolean
)

/**
 * 转换质量分级与诚实预警判定器 (对标任务书 2.6.2 节 & 研发十条军规第一条)
 * 绝不向用户许诺“100% 完美无损还原”，在用户点击转换前显式标识转换层级
 */
object ConversionQualityAdvisor {

    fun assessConversion(
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat,
        isScannedPdfOrImage: Boolean = false
    ): QualityAssessment {
        return when {
            // 1. 无损原生导出 (LOSSLESS)
            (sourceFormat == DocumentFormat.DOCX || sourceFormat == DocumentFormat.XLSX || sourceFormat == DocumentFormat.MARKDOWN) &&
                    targetFormat == DocumentFormat.PDF -> {
                QualityAssessment(
                    tier = ConversionTier.LOSSLESS,
                    fidelityPercentageEstimate = 99,
                    titleWarning = "高保真原生渲染导出",
                    detailedNotice = "由底层离线排版引擎直接生成矢量 PDF，排版样式、字体与表格均可获得高度一致的呈现。",
                    isOcrRecommended = false
                )
            }

            // 2. 纯图片转 PDF
            sourceFormat == DocumentFormat.IMAGE && targetFormat == DocumentFormat.PDF -> {
                QualityAssessment(
                    tier = ConversionTier.LOSSLESS,
                    fidelityPercentageEstimate = 100,
                    titleWarning = "图像容器直接封装",
                    detailedNotice = "图片以原始分辨率封装为 PDF 页面，图像像素 100% 无损保留。",
                    isOcrRecommended = false
                )
            }

            // 3. 扫描件逆向恢复 (OCR_REBUILD)
            isScannedPdfOrImage || (sourceFormat == DocumentFormat.IMAGE && (targetFormat == DocumentFormat.DOCX || targetFormat == DocumentFormat.XLSX)) -> {
                QualityAssessment(
                    tier = ConversionTier.OCR_REBUILD,
                    fidelityPercentageEstimate = 85,
                    titleWarning = "OCR 版面与文字重建 (非无损)",
                    detailedNotice = "源文档包含位图图像或扫描页面，系统将调用本地离线 PP-OCR 识别文字与表格结构。复杂数学公式、艺术字或手写体可能存在识别偏差，建议转换后核对标记文本。",
                    isOcrRecommended = true
                )
            }

            // 4. 标准 PDF 逆向提取 (LAYOUT_REBUILD)
            sourceFormat == DocumentFormat.PDF && (targetFormat == DocumentFormat.DOCX || targetFormat == DocumentFormat.XLSX) -> {
                QualityAssessment(
                    tier = ConversionTier.LAYOUT_REBUILD,
                    fidelityPercentageEstimate = 90,
                    titleWarning = "版面流式重构 (非无损)",
                    detailedNotice = "PDF 本质为固定绝对坐标页面描述语言。系统将根据文本块的拓扑坐标逆向推导段落、标题与表格边框。多栏排版或复杂表格可能出现轻微换行偏移。",
                    isOcrRecommended = false
                )
            }

            // 5. 默认降级版面重建
            else -> {
                QualityAssessment(
                    tier = ConversionTier.LAYOUT_REBUILD,
                    fidelityPercentageEstimate = 88,
                    titleWarning = "通用版面转换",
                    detailedNotice = "按中间模型转换，基本段落与文字可完整保留，但极端排版特性可能略有调整。",
                    isOcrRecommended = false
                )
            }
        }
    }
}
