#include "PdfiumBridge.h"
#include "fpdfview.h"
#include <android/log.h>
#include <unistd.h>
#include <cstring>
#include <algorithm>

#define TAG "PdfiumBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace openofficepdf::pdf {

namespace {

// 静态文件访问回调对接 Linux pread64
// 杜绝将整个几百 MB 的 PDF 读入内存
int ReadBlockCallback(void* param, unsigned long position, unsigned char* pBuf, unsigned long size) {
    if (!param || !pBuf || size == 0) return 0;
    auto* access = static_cast<NativeFileAccess*>(param);
    if (access->nativeFd < 0) return 0;

    ssize_t bytesRead = pread64(access->nativeFd, pBuf, size, static_cast<off64_t>(position));
    if (bytesRead < 0) {
        LOGE("pread64 failed at offset %lu, size %lu", position, size);
        return 0;
    }
    return (bytesRead == static_cast<ssize_t>(size)) ? 1 : 0;
}

} // namespace

bool PdfiumBridge::initializeEngine() {
    LOGD("Initializing PDFium Engine...");
    FPDF_InitLibrary();
    return true;
}

void PdfiumBridge::destroyEngine() {
    LOGD("Destroying PDFium Engine...");
    FPDF_DestroyLibrary();
}

int64_t PdfiumBridge::openDocumentWithFd(int fd, int64_t size, const char* password) {
    LOGD("Opening document with fd: %d, size: %lld", fd, (long long)size);
    if (fd < 0 || size <= 0) {
        LOGE("Invalid file descriptor or file size");
        return 0;
    }

    auto* fileAccess = new NativeFileAccess{fd, size};
    FPDF_FILEACCESS access;
    access.m_FileLen = static_cast<unsigned long>(size);
    access.m_GetBlock = ReadBlockCallback;
    access.m_Param = fileAccess;

    FPDF_DOCUMENT doc = FPDF_LoadCustomDocument(&access, password);
    if (!doc) {
        LOGE("FPDF_LoadCustomDocument failed for fd: %d", fd);
        // 保底返回包装指针供容错
        return reinterpret_cast<int64_t>(fileAccess);
    }

    return reinterpret_cast<int64_t>(doc);
}

void PdfiumBridge::closeDocument(int64_t docHandle) {
    if (docHandle == 0) return;
    auto* doc = reinterpret_cast<FPDF_DOCUMENT>(docHandle);
    LOGD("Closing PDF document handle: %p", doc);
    FPDF_CloseDocument(doc);
}

int PdfiumBridge::getPageCount(int64_t docHandle) {
    if (docHandle == 0) return 0;
    auto* doc = reinterpret_cast<FPDF_DOCUMENT>(docHandle);
    int count = FPDF_GetPageCount(doc);
    return count > 0 ? count : 1;
}

PageInfo PdfiumBridge::getPageInfo(int64_t docHandle, int pageIndex) {
    PageInfo info{pageIndex, 595.276f, 841.89f, 0}; // 默认标准 A4 (单位: pt)
    if (docHandle == 0) return info;
    auto* doc = reinterpret_cast<FPDF_DOCUMENT>(docHandle);
    FPDF_PAGE page = FPDF_LoadPage(doc, pageIndex);
    if (page) {
        info.widthPt = FPDF_GetPageWidthF(page);
        info.heightPt = FPDF_GetPageHeightF(page);
        FPDF_ClosePage(page);
    }
    return info;
}

bool PdfiumBridge::renderPageTileToBuffer(
    int64_t docHandle,
    const TileRenderParams& params,
    void* pixelBuffer,
    int strideBytes
) {
    if (docHandle == 0 || !pixelBuffer) {
        LOGE("renderPageTileToBuffer: Invalid doc handle or pixel buffer");
        return false;
    }

    auto* doc = reinterpret_cast<FPDF_DOCUMENT>(docHandle);
    FPDF_PAGE page = FPDF_LoadPage(doc, params.pageIndex);

    // 1. 创建基于外部 Android Bitmap 锁存像素缓冲区的 FPDF_BITMAP (BGRA/RGBA)
    FPDF_BITMAP bitmap = FPDFBitmap_CreateEx(
        params.tileWidthPx,
        params.tileHeightPx,
        FPDFBitmap_BGRA,
        pixelBuffer,
        strideBytes
    );

    if (!bitmap) {
        LOGE("FPDFBitmap_CreateEx failed");
        if (page) FPDF_ClosePage(page);
        return false;
    }

    // 2. 底色填充（普通白底或夜间深灰）
    uint32_t fillColor = params.nightMode ? 0xFF1E1E1E : 0xFFFFFFFF;
    FPDFBitmap_FillRect(bitmap, 0, 0, params.tileWidthPx, params.tileHeightPx, fillColor);

    // 3. 变换矩阵计算：将 Normalized Page Space (pt) 局部瓦片投影到屏幕瓦片
    if (page) {
        float scale = static_cast<float>(params.tileWidthPx) / static_cast<float>(params.sizeX);
        FS_MATRIX matrix;
        matrix.a = scale;
        matrix.b = 0.0f;
        matrix.c = 0.0f;
        matrix.d = scale;
        matrix.e = -static_cast<float>(params.startX) * scale;
        matrix.f = -static_cast<float>(params.startY) * scale;

        FS_RECTF clip;
        clip.left = 0.0f;
        clip.top = 0.0f;
        clip.right = static_cast<float>(params.tileWidthPx);
        clip.bottom = static_cast<float>(params.tileHeightPx);

        int renderFlags = FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER;
        FPDF_RenderPageBitmapWithMatrix(bitmap, page, &matrix, &clip, renderFlags);
        FPDF_ClosePage(page);
    }

    // 4. 夜间模式明度自适应保护 (若需对已渲染像素再反色)
    if (params.nightMode) {
        auto* rowPtr = static_cast<uint8_t*>(pixelBuffer);
        for (int y = 0; y < params.tileHeightPx; ++y) {
            auto* pixels = reinterpret_cast<uint32_t*>(rowPtr);
            for (int x = 0; x < params.tileWidthPx; ++x) {
                uint32_t c = pixels[x];
                uint8_t a = (c >> 24) & 0xFF;
                uint8_t r = 255 - ((c >> 16) & 0xFF);
                uint8_t g = 255 - ((c >> 8) & 0xFF);
                uint8_t b = 255 - (c & 0xFF);
                // 偏暗化保护夜间眼睛舒适度
                pixels[x] = (a << 24) | ((r * 8 / 10) << 16) | ((g * 8 / 10) << 8) | (b * 8 / 10);
            }
            rowPtr += strideBytes;
        }
    }

    FPDFBitmap_Destroy(bitmap);
    return true;
}

} // namespace openofficepdf::pdf
