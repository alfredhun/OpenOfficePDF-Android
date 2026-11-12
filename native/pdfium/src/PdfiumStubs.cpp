#include "fpdfview.h"
#include "fpdf_edit.h"
#include <cstring>

// Weak symbol implementations for PDFium C API
// Allows linking without requiring heavy prebuilt binaries in CI environments,
// while dynamically overriding when full native binary is provided.
extern "C" {

__attribute__((weak)) void FPDF_InitLibrary(void) {}
__attribute__((weak)) void FPDF_DestroyLibrary(void) {}

__attribute__((weak)) FPDF_DOCUMENT FPDF_LoadCustomDocument(FPDF_FILEACCESS* pFileAccess, const char* password) {
    (void)pFileAccess;
    (void)password;
    static int dummyDoc = 1;
    return &dummyDoc;
}

__attribute__((weak)) void FPDF_CloseDocument(FPDF_DOCUMENT document) {
    (void)document;
}

__attribute__((weak)) int FPDF_GetPageCount(FPDF_DOCUMENT document) {
    (void)document;
    return 1;
}

__attribute__((weak)) FPDF_PAGE FPDF_LoadPage(FPDF_DOCUMENT document, int page_index) {
    (void)document;
    (void)page_index;
    static int dummyPage = 1;
    return &dummyPage;
}

__attribute__((weak)) void FPDF_ClosePage(FPDF_PAGE page) {
    (void)page;
}

__attribute__((weak)) double FPDF_GetPageWidth(FPDF_PAGE page) {
    (void)page;
    return 595.276;
}

__attribute__((weak)) double FPDF_GetPageHeight(FPDF_PAGE page) {
    (void)page;
    return 841.89;
}

__attribute__((weak)) float FPDF_GetPageWidthF(FPDF_PAGE page) {
    (void)page;
    return 595.276f;
}

__attribute__((weak)) float FPDF_GetPageHeightF(FPDF_PAGE page) {
    (void)page;
    return 841.89f;
}

__attribute__((weak)) FPDF_BITMAP FPDFBitmap_CreateEx(int width, int height, int format, void* first_scan, int stride) {
    (void)width;
    (void)height;
    (void)format;
    (void)first_scan;
    (void)stride;
    static int dummyBmp = 1;
    return &dummyBmp;
}

__attribute__((weak)) void FPDFBitmap_FillRect(FPDF_BITMAP bitmap, int left, int top, int width, int height, unsigned int color) {
    (void)bitmap;
    (void)left;
    (void)top;
    (void)width;
    (void)height;
    (void)color;
}

__attribute__((weak)) void FPDFBitmap_Destroy(FPDF_BITMAP bitmap) {
    (void)bitmap;
}

__attribute__((weak)) void FPDF_RenderPageBitmapWithMatrix(
    FPDF_BITMAP bitmap,
    FPDF_PAGE page,
    const FS_MATRIX* matrix,
    const FS_RECTF* clipping,
    int flags
) {
    (void)bitmap;
    (void)page;
    (void)matrix;
    (void)clipping;
    (void)flags;
}

__attribute__((weak)) int FPDFPage_CountObjects(FPDF_PAGE page) {
    (void)page;
    return 0;
}

__attribute__((weak)) FPDF_PAGEOBJECT FPDFPage_GetObject(FPDF_PAGE page, int index) {
    (void)page;
    (void)index;
    return nullptr;
}

__attribute__((weak)) int FPDFPageObj_GetType(FPDF_PAGEOBJECT page_object) {
    (void)page_object;
    return 0;
}

__attribute__((weak)) void FPDFPage_RemoveObject(FPDF_PAGE page, FPDF_PAGEOBJECT page_object) {
    (void)page;
    (void)page_object;
}

__attribute__((weak)) void FPDFPage_InsertObject(FPDF_PAGE page, FPDF_PAGEOBJECT page_object) {
    (void)page;
    (void)page_object;
}

__attribute__((weak)) int FPDFPage_GenerateContent(FPDF_PAGE page) {
    (void)page;
    return 1;
}

__attribute__((weak)) FPDF_PAGEOBJECT FPDFPageObj_CreateTextObj(FPDF_DOCUMENT document, FPDF_FONT font, float font_size) {
    (void)document;
    (void)font;
    (void)font_size;
    return nullptr;
}

__attribute__((weak)) int FPDFPageObj_GetBounds(FPDF_PAGEOBJECT page_object, float* left, float* bottom, float* right, float* top) {
    (void)page_object;
    if (left) *left = 0;
    if (bottom) *bottom = 0;
    if (right) *right = 0;
    if (top) *top = 0;
    return 1;
}

}
