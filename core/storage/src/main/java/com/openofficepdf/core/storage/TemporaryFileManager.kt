package com.openofficepdf.core.storage

import android.content.Context
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.SessionId
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 沙盒临时文件与大对象 Blob 管理器 (任务书 4.4 节)
 * 具备引用计数 (Pin Count) 保护，防止活跃编辑被清理线程误删
 */
open class TemporaryFileManager(context: Context? = null) {

    private val baseDir = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir", "."), "openoffice_temp")
    private val filesDir = context?.filesDir ?: File(System.getProperty("java.io.tmpdir", "."), "openoffice_files")
    private val stagingDir = File(baseDir, "staging").apply { mkdirs() }
    private val workingCopyDir = File(baseDir, "working_copies").apply { mkdirs() }
    private val blobDir = File(filesDir, "blobs").apply { mkdirs() }

    // 路径 -> 活跃引用计数
    private val pinCounts = ConcurrentHashMap<String, AtomicInteger>()

    open fun pin(file: File) {
        pinCounts.computeIfAbsent(file.absolutePath) { AtomicInteger(0) }.incrementAndGet()
    }

    open fun unpin(file: File) {
        pinCounts[file.absolutePath]?.let { counter ->
            if (counter.decrementAndGet() <= 0) {
                pinCounts.remove(file.absolutePath)
            }
        }
    }

    open fun isPinned(file: File): Boolean {
        return (pinCounts[file.absolutePath]?.get() ?: 0) > 0
    }

    open fun pinFile(file: File) = pin(file)
    open fun unpinFile(file: File) = unpin(file)

    open fun createStagingFile(sessionId: String): File {
        val file = File(stagingDir, "${sessionId}_${System.currentTimeMillis()}.tmp")
        pin(file)
        return file
    }

    open fun createStagingCopy(sessionId: SessionId, prefix: String = "staging_"): EngineResult<File> {
        return try {
            val file = File(stagingDir, "${prefix}${sessionId.value}_${System.currentTimeMillis()}.tmp")
            pin(file)
            EngineResult.success(file)
        } catch (e: Exception) {
            EngineResult.failure(EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED, "Failed to create staging copy: ${e.message}")
        }
    }

    open fun getTemporaryFile(relativePath: String): File = File(stagingDir, relativePath)

    open fun releaseSessionStaging(sessionId: SessionId): Long = 0L

    open fun createWorkingCopyFile(sessionId: String, extension: String): File {
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        val file = File(workingCopyDir, "${sessionId}_work$ext")
        pin(file)
        return file
    }

    open fun createBlobFile(documentId: String, blobId: String): File {
        val file = File(blobDir, "${documentId}_$blobId.blob")
        return file
    }

    /**
     * 启动时扫描并清理无活跃引用的孤儿文件
     */
    fun cleanOrphanFiles() {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 60 * 60 * 1000

        sequenceOf(stagingDir, workingCopyDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (!isPinned(file) && file.lastModified() < oneDayAgo) {
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

/**
 * 磁盘缓存配额管理 (任务书 4.4 节)
 * 默认上限 1024 MB，LRU 淘汰
 */
class CacheQuotaManager(
    private val context: Context,
    private val tempFileManager: TemporaryFileManager,
    private var maxQuotaBytes: Long = 1024L * 1024 * 1024 // 1GB
) {

    fun setQuotaBytes(bytes: Long) {
        maxQuotaBytes = bytes
    }

    fun enforceQuota() {
        val cacheDir = context.cacheDir ?: return
        var currentSize = computeDirectorySize(cacheDir)
        if (currentSize <= maxQuotaBytes) return

        // 收集所有未被 Pin 的文件按修改时间升序排列 (最久未访问优先淘汰)
        val candidateFiles = cacheDir.walkTopDown()
            .filter { it.isFile && !tempFileManager.isPinned(it) }
            .sortedBy { it.lastModified() }
            .toList()

        for (file in candidateFiles) {
            val length = file.length()
            if (file.delete()) {
                currentSize -= length
                if (currentSize <= maxQuotaBytes * 0.85) { // 降至 85% 水位线停止
                    break
                }
            }
        }
    }

    private fun computeDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach {
            if (it.isFile) size += it.length()
        }
        return size
    }
}
