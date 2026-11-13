package com.openofficepdf.core.ipc

import android.content.Context
import android.os.IBinder
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.SessionAndEditDao
import com.openofficepdf.core.database.entity.DocumentSessionEntity
import com.openofficepdf.core.database.entity.EditOperationEntity
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.storage.TemporaryFileManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ProcessCrashRecoveryCoordinatorTest {

    private lateinit var coordinator: ProcessCrashRecoveryCoordinator
    private lateinit var mockConnectionManager: EngineConnectionManager
    private lateinit var mockSessionDao: SessionAndEditDao
    private lateinit var mockTempFileManager: TemporaryFileManager

    @Before
    fun setUp() {
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getPackageName(): String = "com.openofficepdf.test"
        }

        mockConnectionManager = EngineConnectionManager(
            context = mockContext,
            serviceAction = "com.openofficepdf.action.OFFICE_ENGINE"
        )

        mockSessionDao = object : SessionAndEditDao {
            val sessions = mutableMapOf<String, DocumentSessionEntity>()
            val ops = mutableMapOf<String, MutableList<EditOperationEntity>>()

            override suspend fun insertSession(session: DocumentSessionEntity) {
                sessions[session.sessionId] = session
            }
            override suspend fun getSession(sessionId: String): DocumentSessionEntity? = sessions[sessionId]
            override suspend fun markSessionDirty(sessionId: String, isDirty: Boolean) {
                sessions[sessionId]?.let { sessions[sessionId] = it.copy(isDirty = isDirty) }
            }
            override suspend fun deleteSession(sessionId: String) {
                sessions.remove(sessionId)
            }
            override suspend fun insertEditOperation(operation: EditOperationEntity) {
                ops.getOrPut(operation.sessionId) { mutableListOf() }.add(operation)
            }
            override suspend fun getOperationsForSession(sessionId: String): List<EditOperationEntity> =
                ops[sessionId] ?: emptyList()
            override suspend fun deleteOperationsForSession(sessionId: String) {
                ops.remove(sessionId)
            }
            override suspend fun insertDocumentIgnore(document: com.openofficepdf.core.database.entity.DocumentEntity) {}
            override suspend fun insertSessionIgnore(session: DocumentSessionEntity) {
                sessions[session.sessionId] = session
            }
        }

        mockTempFileManager = object : TemporaryFileManager() {
            override fun createStagingCopy(sessionId: SessionId, prefix: String): EngineResult<File> =
                EngineResult.success(File("/dummy"))
            override fun getTemporaryFile(relativePath: String): File = File("/dummy")
            override fun releaseSessionStaging(sessionId: SessionId): Long = 0L
            override fun pinFile(file: File) {}
            override fun unpinFile(file: File) {}
        }

        coordinator = ProcessCrashRecoveryCoordinator(
            context = mockContext,
            connectionManager = mockConnectionManager,
            sessionDao = mockSessionDao,
            tempFileManager = mockTempFileManager,
            engineName = "OfficeTestEngine"
        )
    }

    @Test
    fun testDeathNotificationTriggerRecovery() = runBlocking {
        val sessionId = SessionId("session_crash_001")

        // 模拟崩溃前在数据库记录了会话与未保存操作
        mockSessionDao.insertSession(
            DocumentSessionEntity(
                sessionId = sessionId.value,
                documentId = "doc_001",
                lastOpenedPage = 2,
                activeTool = "EDIT",
                isDirty = true,
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
        )
        mockSessionDao.insertEditOperation(
            EditOperationEntity(
                operationId = "op_001",
                sessionId = sessionId.value,
                pageIndex = 2,
                operationType = "TYPE_TEXT",
                operationPayloadJson = "{\"text\":\"crash_rescue\"}",
                blobPath = null,
                timestamp = System.currentTimeMillis()
            )
        )

        // 触发恢复并断言从数据库恢复的脏会话与未提交操作数
        val result = coordinator.restoreSessionState(sessionId)
        assertTrue("Expected success but got failure: ${(result as? EngineResult.Failure)?.message}", result is EngineResult.Success)
        assertEquals(1, (result as EngineResult.Success).data)
    }
}
