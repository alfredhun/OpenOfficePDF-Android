#pragma once

#include "fpdfview.h"
#include "fpdf_edit.h"
#include <string>
#include <vector>

namespace openofficepdf::pdf {

/**
 * 原生 PDF 文本对象信息
 */
struct NativeTextObjectInfo {
    int objectIndex;
    float leftPt;
    float bottomPt;
    float rightPt;
    float topPt;
    float fontSizePt;
    std::string textContent;
    std::string fontName;
};

/**
 * 原生 Redaction 脱敏区域 (Normalized Page Space: pt)
 */
struct NativeRedactRegion {
    float left;
    float top;
    float right;
    float bottom;
};

class PdfiumObjectEditor {
public:
    /**
     * 枚举指定页面中的所有原生文字对象
     */
    static std::vector<NativeTextObjectInfo> extractTextObjects(FPDF_DOCUMENT doc, int pageIndex);

    /**
     * 二级策略：原位修改原有文字对象内容 (若超出子集字体字符集，由调用方回退)
     */
    static bool updateTextObject(
        FPDF_DOCUMENT doc,
        int pageIndex,
        int objectIndex,
        const char* newText,
        float newFontSizePt,
        FPDF_FONT fallbackFont = nullptr
    );

    /**
     * 真正的 Redaction 脱敏操作：物理剔除相交文本对象并重新生成 Content Stream
     */
    static int applyTrueRedaction(
        FPDF_DOCUMENT doc,
        int pageIndex,
        const std::vector<NativeRedactRegion>& redactRegions
    );
};

} // namespace openofficepdf::pdf
