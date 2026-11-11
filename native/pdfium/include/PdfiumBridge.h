#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace openofficepdf::pdf {

/**
 * PDF 自定义文件随机读取结构体与桥接接口定义
 * 对接 Linux pread64(fd, buffer, size, offset)
 */
struct NativeFileAccess {
    int nativeFd;
    int64_t fileSize;
};

/**
 * 页面基本信息
 */
struct PageInfo {
    int pageIndex;
    float widthPt;
    float heightPt;
    int rotationDegrees; // 0, 90, 180, 270
};

/**
 * 渲染参数结构体
 */
struct TileRenderParams {
    int pageIndex;
    int startX;
    int startY;
    int sizeX;
    int sizeY;
    int tileWidthPx;
    int tileHeightPx;
    int flags; // FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER etc.
    bool nightMode;
};

class PdfiumBridge {
public:
    static bool initializeEngine();
    static void destroyEngine();

    static int64_t openDocumentWithFd(int fd, int64_t size, const char* password = nullptr);
    static void closeDocument(int64_t docHandle);

    static int getPageCount(int64_t docHandle);
    static PageInfo getPageInfo(int64_t docHandle, int pageIndex);

    /**
     * 瓦片渲染管线核心函数：直接渲染到 Android Bitmap 的原生锁存像素缓冲区 (ARGB_8888)
     */
    static bool renderPageTileToBuffer(
        int64_t docHandle,
        const TileRenderParams& params,
        void* pixelBuffer,
        int strideBytes
    );
};

} // namespace openofficepdf::pdf
