#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// PDFium 基础句柄类型
typedef void* FPDF_DOCUMENT;
typedef void* FPDF_PAGE;
typedef void* FPDF_BITMAP;
typedef void* FPDF_PAGEOBJECT;
typedef void* FPDF_FONT;

// 像素格式定义
#define FPDFBitmap_Gray 1
#define FPDFBitmap_BGR  2
#define FPDFBitmap_BGRx 3
#define FPDFBitmap_BGRA 4

// 渲染标志定义
#define FPDF_ANNOT             0x01
#define FPDF_LCD_TEXT          0x02
#define FPDF_NO_NATIVETEXT     0x04
#define FPDF_GRAYSCALE         0x08
#define FPDF_DEBUG_INFO        0x80
#define FPDF_NO_CATCH          0x100
#define FPDF_RENDER_LIMITEDIMAGECACHE 0x200
#define FPDF_RENDER_FORCEHALFTONE     0x400
#define FPDF_PRINTING          0x800
#define FPDF_REVERSE_BYTE_ORDER 0x1000

// 文件读取回调契约结构体 (pread64 核心对接)
typedef struct FPDF_FILEACCESS_ {
    unsigned long m_FileLen;
    int (*m_GetBlock)(void* param, unsigned long position, unsigned char* pBuf, unsigned long size);
    void* m_Param;
} FPDF_FILEACCESS;

// 矩阵结构
typedef struct FS_MATRIX_ {
    float a;
    float b;
    float c;
    float d;
    float e;
    float f;
} FS_MATRIX;

typedef struct FS_RECTF_ {
    float left;
    float top;
    float right;
    float bottom;
} FS_RECTF;

// 核心生命周期与文档加载
void FPDF_InitLibrary(void);
void FPDF_DestroyLibrary(void);
FPDF_DOCUMENT FPDF_LoadCustomDocument(FPDF_FILEACCESS* pFileAccess, const char* password);
void FPDF_CloseDocument(FPDF_DOCUMENT document);
int FPDF_GetPageCount(FPDF_DOCUMENT document);

// 页面操作
FPDF_PAGE FPDF_LoadPage(FPDF_DOCUMENT document, int page_index);
void FPDF_ClosePage(FPDF_PAGE page);
double FPDF_GetPageWidth(FPDF_PAGE page);
double FPDF_GetPageHeight(FPDF_PAGE page);
float FPDF_GetPageWidthF(FPDF_PAGE page);
float FPDF_GetPageHeightF(FPDF_PAGE page);

// 瓦片渲染管线
FPDF_BITMAP FPDFBitmap_CreateEx(int width, int height, int format, void* first_scan, int stride);
void FPDFBitmap_FillRect(FPDF_BITMAP bitmap, int left, int top, int width, int height, unsigned int color);
void FPDFBitmap_Destroy(FPDF_BITMAP bitmap);
void FPDF_RenderPageBitmapWithMatrix(
    FPDF_BITMAP bitmap,
    FPDF_PAGE page,
    const FS_MATRIX* matrix,
    const FS_RECTF* clipping,
    int flags
);

#ifdef __cplusplus
}
#endif
