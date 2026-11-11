#include "OfficeBridge.h"
#include <android/log.h>
#include <cstring>

#define TAG "OfficeBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace openofficepdf::office {

bool OfficeBridge::initializeKit(const char* installPath) {
    LOGD("Initializing LibreOfficeKit / Collabora with install path: %s", installPath ? installPath : "(null)");
    return true;
}

void OfficeBridge::destroyKit() {
    LOGD("Destroying LibreOfficeKit instance");
}

int OfficeBridge::openDocument(const char* filePath) {
    LOGD("Opening Office Document: %s", filePath ? filePath : "(null)");
    if (!filePath) return -1;
    // 返回模拟有效文档句柄 (实际调用 lokit->documentLoad(filePath))
    return 1001;
}

void OfficeBridge::closeDocument(int docHandle) {
    LOGD("Closing Office Document: %d", docHandle);
}

int OfficeBridge::getPartsCount(int docHandle) {
    // Word 返回 1 (流式多页作为一个 Part)，Excel 返回 Sheet 数量
    return 1;
}

void OfficeBridge::setPart(int docHandle, int partIndex) {
    LOGD("Setting active part: doc=%d, part=%d", docHandle, partIndex);
}

bool OfficeBridge::setDocumentOption(int docHandle, const char* optionKey, const char* optionValue) {
    LOGD("Set Document Option: doc=%d, key=%s, val=%s", docHandle, optionKey, optionValue);
    // 切换移动端流式重排视图: 例如设置 PageViewMode 为 1 (Mobile reflow compact layout)
    return true;
}

bool OfficeBridge::renderTileToBuffer(
    const OfficeRenderTileParams& params,
    void* pixelBuffer,
    int strideBytes
) {
    if (!pixelBuffer || params.canvasWidth <= 0 || params.canvasHeight <= 0) return false;
    LOGD("Render Office Tile: doc=%d, pos=[%d,%d], size=[%d,%d]",
         params.docHandle, params.tilePosX, params.tilePosY, params.canvasWidth, params.canvasHeight);
    return true;
}

bool OfficeBridge::postUnoCommand(int docHandle, const char* command, const char* argumentsJson) {
    LOGD("Post UNO Command: doc=%d, cmd=%s, args=%s", docHandle, command, argumentsJson ? argumentsJson : "{}");
    return true;
}

} // namespace openofficepdf::office
