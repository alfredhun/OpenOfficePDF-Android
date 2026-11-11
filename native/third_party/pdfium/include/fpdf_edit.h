#pragma once

#include "fpdfview.h"

#ifdef __cplusplus
extern "C" {
#endif

#define FPDF_PAGEOBJ_TEXT  1
#define FPDF_PAGEOBJ_PATH  2
#define FPDF_PAGEOBJ_IMAGE 3
#define FPDF_PAGEOBJ_SHADING 4
#define FPDF_PAGEOBJ_FORMTYPE 5

int FPDFPage_CountObjects(FPDF_PAGE page);
FPDF_PAGEOBJECT FPDFPage_GetObject(FPDF_PAGE page, int index);
int FPDFPageObj_GetType(FPDF_PAGEOBJECT page_object);
void FPDFPage_RemoveObject(FPDF_PAGE page, FPDF_PAGEOBJECT page_object);
void FPDFPage_InsertObject(FPDF_PAGE page, FPDF_PAGEOBJECT page_object);
int FPDFPage_GenerateContent(FPDF_PAGE page);

// 文本对象编辑
FPDF_PAGEOBJECT FPDFPageObj_CreateTextObj(FPDF_DOCUMENT document, FPDF_FONT font, float font_size);
int FPDFPageObj_GetBounds(FPDF_PAGEOBJECT page_object, float* left, float* bottom, float* right, float* top);

#ifdef __cplusplus
}
#endif
