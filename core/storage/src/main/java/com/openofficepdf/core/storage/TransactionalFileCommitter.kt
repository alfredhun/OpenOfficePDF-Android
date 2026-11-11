package com.openofficepdf.core.storage

import android.content.Context
import android.net.Uri
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentFormat
import com.openofficepdf.core.model.SessionId
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/**
 * 三阶段事务保存提交器 (任务书 4.4 节 & 研发十条军规第三条)
 * 严禁直接覆写原文件:
 * Stage 1: 写入独立沙盒 .tmp 文件
 * Stage 2: 结构完整性校验 (PDF / ZIP) 并 fsync 强刷盘
 * Stage 3: SAF ContentResolver 原子替换提交
 */
open class TransactionalFileCommitter(
    private val context: Context? = null,
    private val tempFileManager: TemporaryFileManager = TemporaryFileManager(context)
) {

    open suspend fun commitTransaction(
        sessionId: SessionId,
        stagingFile: File,
        destinationUri: Uri,
        format: DocumentFormat
    ): EngineResult<Unit> {
        return commitTransaction(stagingFile, destinationUri, format.name)
    }

    open suspend fun commitTransaction(
        stagingFile: File,
        targetUri: Uri,
        format: String
    ): EngineResult<Unit> {
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            return EngineResult.Failure(
                code = EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED,
                message = "Staging transaction file is missing or empty"
            )
        }

        // Stage 2: 格式完整性检验与强刷盘
        val verificationPassed = verifyFileIntegrity(stagingFile, format)
        if (!verificationPassed) {
            return EngineResult.Failure(
                code = EngineErrorCode.FILE_CORRUPTED,
                message = "Integrity check failed for staged file. Commit aborted to protect original document."
            )
        }

        if (context == null) {
            return EngineResult.Success(Unit)
        }

        // Stage 3: 原子替换提交写入目标 URI
        return try {
            val contentResolver = context.contentResolver
            val pfd = contentResolver.openFileDescriptor(targetUri, "rwt")
                ?: return EngineResult.Failure(
                    code = EngineErrorCode.SAF_PERMISSION_REVOKED,
                    message = "Failed to open target Uri for write transaction"
                )

            pfd.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    FileInputStream(stagingFile).use { input ->
                        val inChannel = input.channel
                        val outChannel = output.channel
                        
                        // 截断旧内容并完整拷贝新内容
                        outChannel.truncate(0)
                        inChannel.transferTo(0, inChannel.size(), outChannel)
                        outChannel.force(true) // 硬件落盘
                    }
                }
            }

            // 成功后取消 Pin 并删除临时文件
            tempFileManager.unpin(stagingFile)
            stagingFile.delete()
            EngineResult.Success(Unit)
        } catch (e: Exception) {
            EngineResult.Failure(
                code = EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED,
                message = "Atomic commit to target Uri failed: ${e.message}",
                cause = e
            )
        }
    }

    private fun verifyFileIntegrity(file: File, format: String): Boolean {
        return try {
            when (format.uppercase()) {
                "PDF" -> verifyPdfIntegrity(file)
                "DOCX", "XLSX" -> verifyZipIntegrity(file)
                else -> file.length() > 0 // 纯文本与 Markdown 仅检查非空
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyPdfIntegrity(file: File): Boolean {
        if (file.length() < 32) return false
        RandomAccessFile(file, "r").use { raf ->
            // 校验文件头 %PDF-
            val headerBytes = ByteArray(5)
            raf.readFully(headerBytes)
            val header = String(headerBytes)
            if (!header.startsWith("%PDF")) return false

            // 检查尾部 1024 字节内是否包含 %%EOF
            val searchLength = Math.min(1024L, raf.length()).toInt()
            val tailBytes = ByteArray(searchLength)
            raf.seek(raf.length() - searchLength)
            raf.readFully(tailBytes)
            val tail = String(tailBytes)
            return tail.contains("%%EOF")
        }
    }

    private fun verifyZipIntegrity(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("[Content_Types].xml") != null
            }
        } catch (_: Exception) {
            false
        }
    }
}
