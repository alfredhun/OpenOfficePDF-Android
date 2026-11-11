package com.openofficepdf.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DocumentAccessProviderImpl(
    private val context: Context,
    private val tempFileManager: TemporaryFileManager
) : DocumentAccessProvider {

    override suspend fun openRandomAccessFd(uri: Uri): EngineResult<RandomAccessHandle> =
        withContext(AppDispatchers.io) {
            try {
                val contentResolver = context.contentResolver
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext EngineResult.Failure(
                        code = EngineErrorCode.FILE_NOT_FOUND,
                        message = "Cannot open ParcelFileDescriptor for Uri: $uri"
                    )

                val nativeFd = pfd.fd
                // 测试是否支持随机 lseek (任务书 4.3 节)
                val isSeekable = try {
                    Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_END)
                    Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_SET)
                    true
                } catch (e: Exception) {
                    false
                }

                if (isSeekable) {
                    val size = pfd.statSize
                    EngineResult.Success(
                        RandomAccessHandle(
                            pfd = pfd,
                            nativeFd = nativeFd,
                            fileSize = size,
                            isFallbackCopy = false
                        )
                    )
                } else {
                    // 不可 seek 时，流式回退拷贝到沙盒临时文件 (Fallback Copy)
                    pfd.close()
                    val fallbackFile = tempFileManager.createWorkingCopyFile("fallback_${System.currentTimeMillis()}", "tmp")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(fallbackFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext EngineResult.Failure(
                        code = EngineErrorCode.FILE_NOT_FOUND,
                        message = "Cannot read sequential stream to create fallback copy"
                    )

                    val fallbackPfd = ParcelFileDescriptor.open(fallbackFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    EngineResult.Success(
                        RandomAccessHandle(
                            pfd = fallbackPfd,
                            nativeFd = fallbackPfd.fd,
                            fileSize = fallbackFile.length(),
                            isFallbackCopy = true
                        )
                    )
                }
            } catch (e: Exception) {
                EngineResult.Failure(
                    code = EngineErrorCode.UNKNOWN_ENGINE_ERROR,
                    message = "Failed to open random access FD: ${e.message}",
                    cause = e
                )
            }
        }

    override suspend fun openSequentialStream(uri: Uri): EngineResult<InputStream> =
        withContext(AppDispatchers.io) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext EngineResult.Failure(
                        code = EngineErrorCode.FILE_NOT_FOUND,
                        message = "InputStream was null for Uri: $uri"
                    )
                EngineResult.Success(stream)
            } catch (e: Exception) {
                EngineResult.Failure(
                    code = EngineErrorCode.UNKNOWN_ENGINE_ERROR,
                    message = "Failed to open sequential stream: ${e.message}",
                    cause = e
                )
            }
        }

    override suspend fun acquirePosixWorkingCopy(
        uri: Uri,
        sessionId: String
    ): EngineResult<WorkingCopyHandle> = withContext(AppDispatchers.io) {
        try {
            val extension = uri.lastPathSegment?.substringAfterLast('.', "tmp") ?: "tmp"
            val copyFile = tempFileManager.createWorkingCopyFile(sessionId, extension)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(copyFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext EngineResult.Failure(
                code = EngineErrorCode.FILE_NOT_FOUND,
                message = "Failed to stream copy to POSIX working copy"
            )

            EngineResult.Success(
                WorkingCopyHandle(
                    localFile = copyFile,
                    releaseAction = {
                        tempFileManager.unpin(copyFile)
                        copyFile.delete()
                    }
                )
            )
        } catch (e: Exception) {
            EngineResult.Failure(
                code = EngineErrorCode.UNKNOWN_ENGINE_ERROR,
                message = "Failed to acquire POSIX working copy: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun beginSaveTransaction(
        targetUri: Uri,
        sessionId: String
    ): EngineResult<TransactionalOutput> = withContext(AppDispatchers.io) {
        try {
            val stagingFile = tempFileManager.createStagingFile(sessionId)
            val outputStream = FileOutputStream(stagingFile)
            EngineResult.Success(
                TransactionalOutput(
                    stagingFile = stagingFile,
                    targetUri = targetUri,
                    outputStream = outputStream
                )
            )
        } catch (e: Exception) {
            EngineResult.Failure(
                code = EngineErrorCode.IO_TRANSACTION_COMMIT_FAILED,
                message = "Failed to create staging transaction: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun persistAndValidatePermission(uri: Uri): EngineResult<PermissionStatus> =
        withContext(AppDispatchers.io) {
            try {
                if (uri.scheme == "content") {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    EngineResult.Success(PermissionStatus.PERSISTED_VALID)
                } else {
                    EngineResult.Success(PermissionStatus.UNSUPPORTED_SCHEME)
                }
            } catch (e: SecurityException) {
                EngineResult.Success(PermissionStatus.REVOKED_OR_EXPIRED)
            } catch (e: Exception) {
                EngineResult.Failure(
                    code = EngineErrorCode.SAF_PERMISSION_REVOKED,
                    message = "Error validating SAF permissions: ${e.message}",
                    cause = e
                )
            }
        }
}
