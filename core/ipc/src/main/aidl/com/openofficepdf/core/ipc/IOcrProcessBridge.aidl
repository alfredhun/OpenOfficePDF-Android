package com.openofficepdf.core.ipc;

import android.os.ParcelFileDescriptor;

/**
 * OCR 与 AI 推理独立进程 AIDL 接口
 */
interface IOcrProcessBridge {
    void startOcrTask(
        String taskId,
        in ParcelFileDescriptor inputPfd,
        int startPage,
        int endPage,
        boolean enableLayout,
        String outputJsonDirPath
    );
    void cancelTask(String taskId);
    boolean ping();
}
