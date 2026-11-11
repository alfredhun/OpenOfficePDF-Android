package com.openofficepdf.core.storage

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentId
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 统一存储与 SAF 访问契约 (任务书 4.2 节)
 */
interface DocumentAccessProvider {

    /**
     * 优先获取可随机读取的文件描述符（供 PDFium Custom File Access 等通过 JNI pread64 按需读取）
     * 若底层 Provider 不支持随机 seek，自动回退至沙盒流式缓存副本并返回其 FD
     */
    suspend fun openRandomAccessFd(uri: Uri): EngineResult<RandomAccessHandle>

    /**
     * 获取顺序读取流（供 Markdown、轻量配置文件读取）
     */
    suspend fun openSequentialStream(uri: Uri): EngineResult<InputStream>

    /**
     * 为强制要求 POSIX 本地文件路径的引擎（如 LibreOffice LOKit）创建受控沙盒工作副本
     */
    suspend fun acquirePosixWorkingCopy(uri: Uri, sessionId: String): EngineResult<WorkingCopyHandle>

    /**
     * 开启三阶段原子保存事务
     */
    suspend fun beginSaveTransaction(targetUri: Uri, sessionId: String): EngineResult<TransactionalOutput>

    /**
     * 顺序读取流（供文件拷贝、预览等直接使用）
     */
    fun openReadOnlyStream(uri: Uri): InputStream =
        throw UnsupportedOperationException("openReadOnlyStream")

    /**
     * 校验文件访问权限
     */
    suspend fun checkFileAccess(uri: Uri): EngineResult<Unit> = EngineResult.Success(Unit)

    /**
     * 持久化并验证 SAF URI 读写权限
     */
    suspend fun persistAndValidatePermission(uri: Uri): EngineResult<PermissionStatus>
}

data class RandomAccessHandle(
    val pfd: ParcelFileDescriptor,
    val nativeFd: Int,
    val fileSize: Long,
    val isFallbackCopy: Boolean
) : Closeable {
    override fun close() = pfd.close()
}

data class WorkingCopyHandle(
    val localFile: File,
    val releaseAction: () -> Unit
) : Closeable {
    override fun close() = releaseAction()
}

data class TransactionalOutput(
    val stagingFile: File,
    val targetUri: Uri,
    val outputStream: OutputStream
)

enum class PermissionStatus {
    PERSISTED_VALID,
    REVOKED_OR_EXPIRED,
    UNSUPPORTED_SCHEME
}
