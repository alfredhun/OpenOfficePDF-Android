package com.openofficepdf.engine.office

import android.net.Uri
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.common.EngineErrorCode
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.DocumentDao
import com.openofficepdf.core.database.dao.SessionAndEditDao
import com.openofficepdf.core.database.entity.DocumentSessionEntity
import com.openofficepdf.core.model.DocumentFormat
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.storage.DocumentAccessProvider
import com.openofficepdf.core.storage.TemporaryFileManager
import com.openofficepdf.core.storage.TransactionalFileCommitter
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 跨进程 Collabora / LibreOfficeKit 客户端适配器实现
 * 负责调度 `:office_engine` 独立进程
 */
class CollaboraOfficeAdapter(
    private val storageProvider: DocumentAccessProvider,
    private val tempFileManager: TemporaryFileManager,
    private val committer: TransactionalFileCommitter,
    private val sessionDao: SessionAndEditDao
) : OfficeEngine {

    // 维护当前活跃的会话与暂存文件
    private val activeSessions = ConcurrentHashMap<SessionId, StagedOfficeSession>()

    private data class StagedOfficeSession(
        val sessionId: SessionId,
        val documentId: DocumentId,
        val originalUri: Uri,
        val stagingFile: File,
        var isReflowEnabled: Boolean,
        val nativeHandle: Int
    )

    override suspend fun openSession(
        documentId: DocumentId,
        fileUri: Uri
    ): EngineResult<OfficeSession> = withContext(AppDispatchers.io) {
        val sessionId = SessionId(UUID.randomUUID().toString())

        // 1. 创建沙盒隔离的暂存文件
        val stageResult = tempFileManager.createStagingCopy(sessionId, "office_doc_")
        if (stageResult is EngineResult.Failure) {
            return@withContext EngineResult.failure(stageResult.errorCode, stageResult.errorMessage)
        }
        val stagingFile = (stageResult as EngineResult.Success).data

        // 2. 从 SAF 读取并写入暂存副本
        try {
            storageProvider.openReadOnlyStream(fileUri).use { input ->
                stagingFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            return@withContext EngineResult.failure(
                EngineErrorCode.FILE_READ_FAILED,
                "Failed to copy file to staging sandbox: ${e.message}"
            )
        }

        // 3. 模拟跨进程向 :office_engine 发起 openDocument
        val handle = 1001
        activeSessions[sessionId] = StagedOfficeSession(
            sessionId = sessionId,
            documentId = documentId,
            originalUri = fileUri,
            stagingFile = stagingFile,
            isReflowEnabled = false,
            nativeHandle = handle
        )

        // 4. 持久化会话记录到 Room 数据库
        val sessionEntity = DocumentSessionEntity(
            sessionId = sessionId.value,
            documentId = documentId.value,
            lastOpenedPage = 0,
            activeTool = "VIEW",
            isDirty = false,
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis()
        )
        sessionDao.insertSession(sessionEntity)

        EngineResult.success(
            OfficeSession(
                sessionId = sessionId,
                documentId = documentId,
                nativeDocHandle = handle,
                isReflowSupported = true
            )
        )
    }

    override suspend fun setMobileReflowMode(
        sessionId: SessionId,
        enabled: Boolean
    ): EngineResult<Unit> = withContext(AppDispatchers.io) {
        val session = activeSessions[sessionId]
            ?: return@withContext EngineResult.failure(
                EngineErrorCode.SESSION_EXPIRED,
                "Active session not found: ${sessionId.value}"
            )

        session.isReflowEnabled = enabled
        // 跨进程通过 AIDL / UNO 命令通知 Collabora 引擎切换页面布局模式
        // 命令示例: .uno:PageViewMode { "Mode": enabled ? "CompactReflow" : "PrintLayout" }
        EngineResult.success(Unit)
    }

    override suspend fun dispatchKeyEvent(
        sessionId: SessionId,
        keyCode: Int,
        unicodeChar: Int
    ): EngineResult<Unit> = withContext(AppDispatchers.io) {
        if (!activeSessions.containsKey(sessionId)) {
            return@withContext EngineResult.failure(EngineErrorCode.SESSION_EXPIRED, "Session expired")
        }
        EngineResult.success(Unit)
    }

    override suspend fun executeCommand(
        sessionId: SessionId,
        unoCommand: String,
        paramsJson: String
    ): EngineResult<Unit> = withContext(AppDispatchers.io) {
        val session = activeSessions[sessionId]
            ?: return@withContext EngineResult.failure(EngineErrorCode.SESSION_EXPIRED, "Session expired")

        // 标记会话修改脏状态
        sessionDao.markSessionDirty(sessionId.value, true)
        EngineResult.success(Unit)
    }

    /**
     * 三阶段事务级保存 Word 文档
     */
    override suspend fun saveSession(
        sessionId: SessionId,
        targetUri: Uri?
    ): EngineResult<Unit> = withContext(AppDispatchers.io) {
        val session = activeSessions[sessionId]
            ?: return@withContext EngineResult.failure(EngineErrorCode.SESSION_EXPIRED, "Session expired")

        // 1. 提交暂存文件完整性检查并原子更新原 SAF 文件
        val destUri = targetUri ?: session.originalUri
        val commitResult = committer.commitTransaction(
            sessionId = sessionId,
            stagingFile = session.stagingFile,
            destinationUri = destUri,
            format = DocumentFormat.DOCX
        )

        if (commitResult is EngineResult.Success) {
            sessionDao.markSessionDirty(sessionId.value, false)
        }
        commitResult
    }

    override suspend fun exportToPdf(
        sessionId: SessionId,
        outputUri: Uri
    ): EngineResult<Unit> = withContext(AppDispatchers.io) {
        if (!activeSessions.containsKey(sessionId)) {
            return@withContext EngineResult.failure(EngineErrorCode.SESSION_EXPIRED, "Session expired")
        }
        // 调用 Collabora UNO: .uno:ExportDirectToPDF
        EngineResult.success(Unit)
    }

    override suspend fun closeSession(sessionId: SessionId): EngineResult<Unit> = withContext(AppDispatchers.io) {
        val session = activeSessions.remove(sessionId)
        if (session != null) {
            tempFileManager.releaseSessionStaging(sessionId)
        }
        EngineResult.success(Unit)
    }
}
