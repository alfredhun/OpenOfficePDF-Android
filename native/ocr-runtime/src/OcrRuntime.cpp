#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#define TAG "OcrRuntime"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

namespace openofficepdf::ocr {

struct DetectedBox {
    float x;
    float y;
    float width;
    float height;
    float confidence;
    std::string text;
};

class OnnxOcrPipeline {
public:
    static bool initModels(const char* detModelPath, const char* clsModelPath, const char* recModelPath, const char* keysPath) {
        LOGD("Init ONNX OCR Models: det=%s, rec=%s", detModelPath, recModelPath);
        return true;
    }

    static std::vector<DetectedBox> runInference(const uint8_t* bgrBuffer, int width, int height) {
        LOGD("Running ONNX OCR inference on %dx%d image", width, height);
        std::vector<DetectedBox> results;
        return results;
    }
};

} // namespace openofficepdf::ocr

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_openofficepdf_engine_ocr_NativeOcrRuntime_nativeInitModels(
    JNIEnv* env,
    jclass clazz,
    jstring detPath,
    jstring clsPath,
    jstring recPath,
    jstring keysPath
) {
    const char* cDet = env->GetStringUTFChars(detPath, nullptr);
    const char* cCls = env->GetStringUTFChars(clsPath, nullptr);
    const char* cRec = env->GetStringUTFChars(recPath, nullptr);
    const char* cKeys = env->GetStringUTFChars(keysPath, nullptr);

    bool ok = openofficepdf::ocr::OnnxOcrPipeline::initModels(cDet, cCls, cRec, cKeys);

    env->ReleaseStringUTFChars(detPath, cDet);
    env->ReleaseStringUTFChars(clsPath, cCls);
    env->ReleaseStringUTFChars(recPath, cRec);
    env->ReleaseStringUTFChars(keysPath, cKeys);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
