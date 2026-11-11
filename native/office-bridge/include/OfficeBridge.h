#pragma once

#include <cstdint>
#include <string>

namespace openofficepdf::office {

/**
 * Collabora / LibreOfficeKit 桥接契约定义 (任务书 7.3 节)
 */
struct OfficeRenderTileParams {
    int docHandle;
    int partIndex;
    int canvasWidth;
    int canvasHeight;
    int tilePosX;
    int tilePosY;
    int tileWidth;
    int tileHeight;
};

class OfficeBridge {
public:
    static bool initializeKit(const char* installPath);
    static void destroyKit();

    static int openDocument(const char* filePath);
    static void closeDocument(int docHandle);

    static int getPartsCount(int docHandle);
    static void setPart(int docHandle, int partIndex);

    /**
     * 移动端自适应重排视图切换 (Mobile Reflow / Compact View)
     * 控制 Collabora/LibreOffice 是否按页面物理宽度换行，还是自适应手机屏幕宽度
     */
    static bool setDocumentOption(int docHandle, const char* optionKey, const char* optionValue);

    /**
     * 渲染指定图块至内存缓冲区
     */
    static bool renderTileToBuffer(
        const OfficeRenderTileParams& params,
        void* pixelBuffer,
        int strideBytes
    );

    /**
     * 执行 UNO 格式与排版命令
     * 例如: .uno:Bold, .uno:Italic, .uno:InsertTable, .uno:Save
     */
    static bool postUnoCommand(int docHandle, const char* command, const char* argumentsJson);
};

} // namespace openofficepdf::office
