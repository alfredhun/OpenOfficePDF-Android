#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "PdfiumBridge.h"
#include "PdfiumObjectEditor.h"

#define TAG "PdfiumJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace openofficepdf::pdf;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeInit(JNIEnv* env, jclass clazz) {
    return PdfiumBridge::initializeEngine() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeDestroy(JNIEnv* env, jclass clazz) {
    PdfiumBridge::destroyEngine();
}

JNIEXPORT jlong JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeOpenDocumentWithFd(
    JNIEnv* env,
    jclass clazz,
    jint fd,
    jlong size,
    jstring password
) {
    const char* pwdChars = nullptr;
    if (password != nullptr) {
        pwdChars = env->GetStringUTFChars(password, nullptr);
    }
    jlong handle = PdfiumBridge::openDocumentWithFd(fd, size, pwdChars);
    if (pwdChars != nullptr) {
        env->ReleaseStringUTFChars(password, pwdChars);
    }
    return handle;
}

JNIEXPORT void JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeCloseDocument(
    JNIEnv* env,
    jclass clazz,
    jlong docHandle
) {
    PdfiumBridge::closeDocument(docHandle);
}

JNIEXPORT jint JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeGetPageCount(
    JNIEnv* env,
    jclass clazz,
    jlong docHandle
) {
    return PdfiumBridge::getPageCount(docHandle);
}

JNIEXPORT jfloatArray JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeGetPageInfo(
    JNIEnv* env,
    jclass clazz,
    jlong docHandle,
    jint pageIndex
) {
    PageInfo info = PdfiumBridge::getPageInfo(docHandle, pageIndex);
    jfloatArray result = env->NewFloatArray(3);
    jfloat values[3] = {info.widthPt, info.heightPt, static_cast<float>(info.rotationDegrees)};
    env->SetFloatArrayRegion(result, 0, 3, values);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeRenderTile(
    JNIEnv* env,
    jclass clazz,
    jlong docHandle,
    jint pageIndex,
    jint startX,
    jint startY,
    jint sizeX,
    jint sizeY,
    jint tileWidthPx,
    jint tileHeightPx,
    jboolean nightMode,
    jobject targetBitmap
) {
    AndroidBitmapInfo bitmapInfo;
    int ret;
    if ((ret = AndroidBitmap_getInfo(env, targetBitmap, &bitmapInfo)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return JNI_FALSE;
    }

    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888!");
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if ((ret = AndroidBitmap_lockPixels(env, targetBitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
        return JNI_FALSE;
    }

    TileRenderParams params;
    params.pageIndex = pageIndex;
    params.startX = startX;
    params.startY = startY;
    params.sizeX = sizeX;
    params.sizeY = sizeY;
    params.tileWidthPx = tileWidthPx;
    params.tileHeightPx = tileHeightPx;
    params.flags = 0;
    params.nightMode = (nightMode == JNI_TRUE);

    bool success = PdfiumBridge::renderPageTileToBuffer(
        docHandle,
        params,
        pixels,
        bitmapInfo.stride
    );

    AndroidBitmap_unlockPixels(env, targetBitmap);
    return success ? JNI_TRUE : JNI_FALSE;
}

// 二级原位编辑 JNI
JNIEXPORT jboolean JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeUpdateTextObject(
    JNIEnv* env,
    jclass clazz,
    jlong docHandle,
    jint pageIndex,
    jint objectIndex,
    jstring newText,
    jfloat newFontSizePt
) {
    if (docHandle == 0 || newText == nullptr) return JNI_FALSE;
    auto* doc = reinterpret_cast<FPDF_DOCUMENT>(docHandle);
    const char* cText = env->GetStringUTFChars(newText, nullptr);

    bool ok = PdfiumObjectEditor::updateTextObject(doc, pageIndex, objectIndex, cText, newFontSizePt);

    env->ReleaseStringUTFChars(newText, cText);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// 真 Redaction 物理擦除 JNI
JNIEXPORT jint JNICALL
Java_com_openofficepdf_engine_pdf_PdfiumNativeBridge_nativeApplyTrueRedaction(
    JNIEnv* env,
    jclass clazz,
    jlong docHandle,
    jint pageIndex,
    jfloatArray regionsFlat // [l, t, r, b, ...]
) {
    if (docHandle == 0 || regionsFlat == nullptr) return 0;
    auto* doc = reinterpret_cast<FPDF_DOCUMENT>(docHandle);

    jsize len = env->GetArrayLength(regionsFlat);
    if (len % 4 != 0) return 0;

    jfloat* elements = env->GetFloatArrayElements(regionsFlat, nullptr);
    std::vector<NativeRedactRegion> regions;
    for (int i = 0; i < len; i += 4) {
        regions.push_back({elements[i], elements[i+1], elements[i+2], elements[i+3]});
    }
    env->ReleaseFloatArrayElements(regionsFlat, elements, JNI_ABORT);

    return PdfiumObjectEditor::applyTrueRedaction(doc, pageIndex, regions);
}

} // extern "C"
