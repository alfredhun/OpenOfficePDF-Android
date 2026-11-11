package com.openofficepdf.core.ipc;

import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;

/**
 * Office 独立进程核心 AIDL 接口 (任务书 3.2 节)
 * 严禁直接通过 Binder 传递完整文件或大位图，必须通过 ParcelFileDescriptor 或 SharedMemory
 */
interface IOfficeProcessBridge {
    int openDocumentFd(in ParcelFileDescriptor pfd, String format);
    void setMobileReflowMode(int docHandle, boolean enabled);
    void dispatchKeyEvent(int docHandle, int keyCode, int unicodeChar);
    void executeUnoCommand(int docHandle, String command, String paramsJson);
    boolean exportToPdf(int docHandle, in ParcelFileDescriptor outputPfd);
    void closeDocument(int docHandle);
    boolean ping();
}
